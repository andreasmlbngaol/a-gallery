#!/usr/bin/env python3
"""
Optimize a fixed-size rembg-style segmentation ONNX model (e.g. isnet-general-use)
so it can run at LOWER resolutions (512/768) for a big CPU speedup on low-end
phones -- feeding AGallery's inference-time quality toggle (Eco/Balanced/High).

WHY THIS IS NEEDED (confirmed by inspecting the graph):
IS-Net's decoder resizes are exported as
    sizes = Concat( Shape(src)[0:2] (dynamic N,C) , Constant([H,W]) (BAKED) )
so the channel count follows the input but the spatial H/W are hard-pinned to the
values seen at export time (1024 -> e.g. 32x32). At 768/512 those pinned sizes no
longer match the encoder skip-connections and a Concat fails mid-graph.

This script fixes it by GRAPH SURGERY: every Resize/Upsample is rewritten from a
baked `sizes` to a `scales` factor (computed from the model's own 1024 shapes,
typically x2.0). Scale-based resizes shrink together with the input, so the
skip-connections stay aligned at any input that is a multiple of 32 (512/768/1024).

Artifacts written next to the input, each VERIFIED by a dummy run at 1024/768/512:
  1) <stem>-dynamic.onnx  -> scale-based + dynamic H/W  (the resolution win)
  2) <stem>-int8.onnx     -> the above, weights quantized to INT8 (extra, optional)

Usage:
    pip install onnx onnxruntime numpy sympy
    python3 optimize_isnet.py path/to/isnet-general-use.onnx
    python3 optimize_isnet.py path/to/model.onnx --outdir out --no-int8

If a variant STILL fails verification, paste the full output back: the failing
node name tells us the next op to patch.
"""

import argparse
import hashlib
import os
import sys


def human(n):
    n = float(n)
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{int(n)} B"
        n /= 1024
    return f"{n} B"


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def describe_io(model):
    g = model.graph

    def dims(t):
        return [
            d.dim_value if d.HasField("dim_value") else (d.dim_param or "?")
            for d in t.type.tensor_type.shape.dim
        ]

    print("  inputs:")
    for t in g.input:
        print(f"    - {t.name}: {dims(t)}")
    print("  outputs:")
    for t in g.output:
        print(f"    - {t.name}: {dims(t)}")


def _shape_map(model):
    """tensor name -> [dim ints or None] from static (1024) shape inference."""
    import onnx

    inferred = onnx.shape_inference.infer_shapes(model)
    out = {}

    def rec(vis):
        for vi in vis:
            dims = []
            for d in vi.type.tensor_type.shape.dim:
                dims.append(d.dim_value if d.HasField("dim_value") else None)
            out[vi.name] = dims

    rec(inferred.graph.input)
    rec(inferred.graph.output)
    rec(inferred.graph.value_info)
    return out


def _const_ints(model, name):
    """Return constant initializer / Constant-node value as list[int], or None."""
    import numpy as np
    from onnx import numpy_helper

    for init in model.graph.initializer:
        if init.name == name:
            return [int(v) for v in np.array(numpy_helper.to_array(init)).reshape(-1)]
    for node in model.graph.node:
        if node.op_type == "Constant" and name in node.output:
            for attr in node.attribute:
                if attr.name == "value":
                    return [int(v) for v in np.array(numpy_helper.to_array(attr.t)).reshape(-1)]
    return None


def _target_hw(model, producer, node, src_shape):
    """Work out the resize target [H,W] at 1024 for a Resize/Upsample node.
    Handles: direct constant sizes, and sizes built by a Concat that mixes a
    dynamic N,C part with a baked H,W constant."""
    sizes_name = node.input[3] if len(node.input) > 3 and node.input[3] else None
    if sizes_name:
        direct = _const_ints(model, sizes_name)
        if direct and len(direct) == 4:
            return [direct[2], direct[3]]
        if direct and len(direct) == 2:
            return direct
        p = producer.get(sizes_name)
        if p is not None and p.op_type == "Concat":
            const_flat = []
            for ci in p.input:
                cc = _const_ints(model, ci)
                if cc is not None:
                    const_flat.extend(cc)
            if len(const_flat) == 2:
                return const_flat
            if len(const_flat) == 4:
                return [const_flat[2], const_flat[3]]
    return None


def sizes_to_scales(model):
    """Rewrite Resize/Upsample nodes to scale-based. Returns (converted, total)."""
    import numpy as np
    from onnx import numpy_helper

    shapes = _shape_map(model)
    producer = {}
    for n in model.graph.node:
        for o in n.output:
            producer[o] = n

    converted = 0
    total = 0
    new_inits = []
    for node in model.graph.node:
        if node.op_type not in ("Resize", "Upsample"):
            continue
        total += 1
        src = shapes.get(node.input[0]) if node.input else None
        if not (src and len(src) == 4 and src[2] and src[3]):
            continue
        target = _target_hw(model, producer, node, src)
        if not (target and len(target) == 2 and target[0] and target[1]):
            continue
        scale_h = float(target[0]) / float(src[2])
        scale_w = float(target[1]) / float(src[3])
        if scale_h <= 0 or scale_w <= 0:
            continue
        scales = np.array([1.0, 1.0, scale_h, scale_w], dtype=np.float32)
        sname = node.output[0] + "_scales_fixed"
        new_inits.append(numpy_helper.from_array(scales, name=sname))
        ins = list(node.input)
        while len(ins) < 3:
            ins.append("")
        ins[2] = sname
        del node.input[:]
        node.input.extend([ins[0], ins[1], ins[2]])  # X, roi(empty), scales; drop sizes
        converted += 1

    model.graph.initializer.extend(new_inits)
    return converted, total


def make_dynamic(model):
    def loosen(vis):
        for t in vis:
            dim = t.type.tensor_type.shape.dim
            if len(dim) == 4:
                dim[2].ClearField("dim_value"); dim[2].dim_param = "height"
                dim[3].ClearField("dim_value"); dim[3].dim_param = "width"

    loosen(model.graph.input)
    loosen(model.graph.output)
    del model.graph.value_info[:]
    return model


def verify(path, sizes):
    try:
        import numpy as np
        import onnxruntime as ort
    except Exception as e:
        print(f"  [skip verify] onnxruntime/numpy not available: {e}")
        return True
    try:
        sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    except Exception as e:
        print(f"  [FAIL] could not load: {e}")
        return False
    inp = sess.get_inputs()[0]
    ok = True
    for (h, w) in sizes:
        x = np.random.rand(1, 3, h, w).astype(np.float32)
        try:
            sess.run(None, {inp.name: x})
            print(f"  [ok] ran at {w}x{h}")
        except Exception as e:
            ok = False
            print(f"  [FAIL] {w}x{h}: {str(e).splitlines()[0]}")
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("--outdir", default=None)
    ap.add_argument("--no-int8", action="store_true")
    args = ap.parse_args()

    try:
        import onnx
    except Exception as e:
        print(f"error: onnx not installed ({e}). Run: pip install onnx onnxruntime numpy sympy")
        return 2

    src = args.input
    if not os.path.isfile(src):
        print(f"error: no such file: {src}")
        return 2

    outdir = args.outdir or os.path.dirname(os.path.abspath(src))
    os.makedirs(outdir, exist_ok=True)
    stem = os.path.splitext(os.path.basename(src))[0]
    dyn_path = os.path.join(outdir, f"{stem}-dynamic.onnx")
    int8_path = os.path.join(outdir, f"{stem}-int8.onnx")

    print(f"\n== Source: {src} ({human(os.path.getsize(src))}) ==")
    model = onnx.load(src)
    describe_io(model)

    print("\n== Graph surgery: converting baked-size Resize -> scale-based ==")
    converted, total = sizes_to_scales(model)
    print(f"  converted {converted}/{total} Resize/Upsample node(s) to scale-based")
    if converted == 0:
        print("  [warn] nothing converted -- the size pattern differs; send output back.")
    make_dynamic(model)
    try:
        model = onnx.shape_inference.infer_shapes(model)
    except Exception as e:
        print(f"  [warn] shape inference skipped: {e}")
    onnx.save(model, dyn_path)
    print(f"  wrote {dyn_path} ({human(os.path.getsize(dyn_path))})")
    dyn_ok = verify(dyn_path, sizes=[(1024, 1024), (768, 768), (512, 512)])
    if dyn_ok:
        print("  => SUCCESS: dynamic model runs at 512/768/1024.")
        print(f"  => sha256: {sha256_of(dyn_path)}")
    else:
        print("  => still failing; paste the full output (failing node name = next patch).")

    if not args.no_int8:
        print(f"\n== INT8 variant -> {int8_path} ==")
        try:
            from onnxruntime.quantization import quantize_dynamic, QuantType
            from onnxruntime.quantization.shape_inference import quant_pre_process
            base = dyn_path if dyn_ok else src
            prepped = os.path.join(outdir, f"{stem}-prep.onnx")
            try:
                quant_pre_process(base, prepped)
            except Exception as e:
                print(f"  [warn] quant_pre_process skipped ({str(e).splitlines()[0]}); quantizing base directly")
                prepped = base
            quantize_dynamic(prepped, int8_path, weight_type=QuantType.QInt8)
            if prepped != base and os.path.exists(prepped):
                os.remove(prepped)
            print(f"  size: {human(os.path.getsize(int8_path))}")
            sizes = [(1024, 1024), (768, 768), (512, 512)] if dyn_ok else [(1024, 1024)]
            if verify(int8_path, sizes=sizes):
                print("  => INT8 model OK.")
                print(f"  => sha256: {sha256_of(int8_path)}")
        except Exception as e:
            print(f"  [FAIL] INT8 quantization failed: {str(e).splitlines()[0]}")

    print("\nDone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
