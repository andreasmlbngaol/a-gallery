#!/usr/bin/env python3
"""
xnnpack_graph_check.py — inspect ONNX models for XNNPACK-friendliness.

Goal: figure out, per model, whether ONNX Runtime's XNNPACK execution provider
could accelerate it — and in particular whether it contains the bilinear
`Resize` node that makes XNNPACK abort the session build in AGallery
(`xnn_create_resize_bilinear2d_nhwc_fp32` failure). The Conv-heavy models are
the ones that could actually benefit; transformer-heavy graphs (lots of MatMul /
Softmax / LayerNorm / Transpose) fall back to CPU anyway.

Usage:
    python3 xnnpack_graph_check.py                 # scans ./models
    python3 xnnpack_graph_check.py path/to/models  # scans a directory
    python3 xnnpack_graph_check.py a.onnx b.onnx    # scans specific files

Install:
    pip install onnx          # required (static graph scan)
    pip install onnxruntime   # optional (empirical XNNPACK partition test,
                              #  only runs if this build ships the Xnnpack EP)

NOTE ON THE SUPPORTED-OP LIST: XNNPACK EP's supported operator set varies by
ONNX Runtime version/build (and the Android build differs from desktop). The
list below is a *heuristic*, conservative snapshot — the op histogram and the
Resize dump are the ground truth. Edit XNNPACK_SUPPORTED to match your ORT
version if you want a tighter verdict.
"""

import os
import sys
import glob
from collections import Counter

try:
    import onnx
    from onnx import AttributeProto
except ImportError:
    sys.exit("[!] The 'onnx' package is required: pip install onnx")

# Heuristic: ONNX ops the XNNPACK EP is generally able to take. Anything NOT in
# here forces a CPU-fallback partition (and a graph split). Keep this
# conservative; verify against your ORT version.
XNNPACK_SUPPORTED = {
    "Conv", "ConvTranspose", "QLinearConv",
    "MaxPool", "AveragePool", "GlobalAveragePool",
    "Softmax", "Gemm", "MatMul",
    "Add", "Sub", "Mul", "Div", "Max", "Min",
    "Relu", "Clip", "Sigmoid", "HardSwish", "LeakyRelu", "PRelu", "Elu",
    "Abs", "Ceil", "Floor", "Negate", "Square", "Concat",
    "Resize",  # only SOME configs; see the per-node Resize dump below
}

# Ops that most often prevent acceleration on a Conv-style model or signal a
# transformer block (heavy CPU fallback). Highlighted in the report.
NOTEWORTHY_FALLBACK = {
    "Resize", "Transpose", "Reshape", "LayerNormalization", "InstanceNormalization",
    "ReduceMean", "Erf", "Gelu", "Einsum", "Where", "Expand", "Gather",
    "ScatterND", "Pad", "Slice", "Split", "Pow", "Sqrt", "Tanh", "Softplus",
    "DepthToSpace", "SpaceToDepth", "PixelShuffle",
}


def attr_value(attr):
    """Best-effort stringification of an ONNX attribute value."""
    t = attr.type
    if t == AttributeProto.STRING:
        return attr.s.decode("utf-8", "replace")
    if t == AttributeProto.INT:
        return attr.i
    if t == AttributeProto.FLOAT:
        return attr.f
    if t == AttributeProto.INTS:
        return list(attr.ints)
    if t == AttributeProto.FLOATS:
        return list(attr.floats)
    if t == AttributeProto.STRINGS:
        return [s.decode("utf-8", "replace") for s in attr.strings]
    return f"<type {t}>"


def shape_of(value_info):
    dims = []
    tt = value_info.type.tensor_type
    for d in tt.shape.dim:
        if d.dim_param:
            dims.append(d.dim_param)          # dynamic (named) dim
        elif d.HasField("dim_value"):
            dims.append(d.dim_value)
        else:
            dims.append("?")
    return dims


def analyze(path):
    print("=" * 78)
    print(f"MODEL: {os.path.basename(path)}")
    print("=" * 78)
    try:
        model = onnx.load(path)
    except Exception as e:
        print(f"  [!] failed to load: {e}")
        return None

    opsets = {imp.domain or "ai.onnx": imp.version for imp in model.opset_import}
    print(f"  ir_version={model.ir_version}  opset={opsets}")

    g = model.graph
    for i in g.input:
        print(f"  input  {i.name:<20} shape={shape_of(i)}")
    for o in g.output:
        print(f"  output {o.name:<20} shape={shape_of(o)}")

    ops = Counter(n.op_type for n in g.node)
    total = sum(ops.values())
    print(f"\n  nodes: {total} | distinct op types: {len(ops)}")
    print("  op histogram (desc):")
    for op, c in ops.most_common():
        flags = []
        if op not in XNNPACK_SUPPORTED:
            flags.append("NOT-xnnpack")
        if op in NOTEWORTHY_FALLBACK:
            flags.append("fallback")
        tag = ("  <- " + ", ".join(flags)) if flags else ""
        print(f"    {c:>5}  {op}{tag}")

    # Resize detail — the exact thing that breaks XNNPACK session build.
    resize_nodes = [n for n in g.node if n.op_type == "Resize"]
    if resize_nodes:
        print(f"\n  [!] {len(resize_nodes)} Resize node(s) — XNNPACK-critical:")
        for n in resize_nodes:
            attrs = {a.name: attr_value(a) for a in n.attribute}
            mode = attrs.get("mode", "nearest")
            ctm = attrs.get("coordinate_transformation_mode", "half_pixel")
            print(f"      - {n.name or '<unnamed>'}: mode={mode!r} "
                  f"coordinate_transformation_mode={ctm!r} "
                  f"(inputs={len(n.input)})")
        print("      NOTE: 'linear'/'cubic' Resize is what fails the XNNPACK")
        print("      build in the app; 'nearest' is more likely to be accepted.")
    else:
        print("\n  [ok] no Resize nodes.")

    unsupported = {op: c for op, c in ops.items() if op not in XNNPACK_SUPPORTED}
    conv_like = ops.get("Conv", 0) + ops.get("ConvTranspose", 0)
    unsup_count = sum(unsupported.values())

    # Heuristic verdict.
    verdict = []
    if resize_nodes and any(
        (({a.name: attr_value(a) for a in n.attribute}).get("mode", "nearest")
         in ("linear", "cubic"))
        for n in resize_nodes
    ):
        verdict.append("BLOCKED: has linear/cubic Resize (likely XNNPACK build failure)")
    if unsupported:
        verdict.append(f"{unsup_count} node(s) across {len(unsupported)} op type(s) "
                       f"would fall back to CPU: {sorted(unsupported)}")
    if conv_like and not unsupported:
        verdict.append("GOOD candidate: Conv-heavy, all ops in supported set")
    if not verdict:
        verdict.append("review op histogram above")
    print("\n  VERDICT (heuristic):")
    for v in verdict:
        print(f"    - {v}")

    return {
        "model": os.path.basename(path),
        "nodes": total,
        "conv_like": conv_like,
        "resize": len(resize_nodes),
        "resize_linear": sum(
            1 for n in resize_nodes
            if ({a.name: attr_value(a) for a in n.attribute}).get("mode", "nearest")
            in ("linear", "cubic")
        ),
        "unsupported_ops": sorted(unsupported),
    }


def maybe_empirical_xnnpack(path):
    """If this ORT build ships the Xnnpack EP, actually build a session and
    report node placement. Skipped gracefully otherwise."""
    try:
        import onnxruntime as ort
    except ImportError:
        return
    if "XnnpackExecutionProvider" not in ort.get_available_providers():
        return  # desktop CPU wheels usually don't ship it
    print(f"\n  [empirical] building XNNPACK session for {os.path.basename(path)} ...")
    so = ort.SessionOptions()
    so.log_severity_level = 1  # INFO: prints "Node(s) placed on [<EP>]..."
    try:
        ort.InferenceSession(
            path,
            sess_options=so,
            providers=[
                ("XnnpackExecutionProvider", {"intra_op_num_threads": 4}),
                "CPUExecutionProvider",
            ],
        )
        print("  [empirical] session built OK (see 'placed on' log lines above).")
    except Exception as e:
        print(f"  [empirical] XNNPACK session build FAILED: {e}")


def collect(args):
    if not args:
        args = ["models"]
    files = []
    for a in args:
        if os.path.isdir(a):
            files += sorted(glob.glob(os.path.join(a, "*.onnx")))
        elif os.path.isfile(a):
            files.append(a)
        else:
            print(f"[!] skipping (not found): {a}")
    return files


def main():
    files = collect(sys.argv[1:])
    if not files:
        sys.exit("[!] no .onnx files found.")
    summary = []
    for f in files:
        row = analyze(f)
        if row:
            summary.append(row)
        maybe_empirical_xnnpack(f)
        print()

    # Compact summary table.
    print("=" * 78)
    print("SUMMARY")
    print("=" * 78)
    hdr = f"{'model':<40}{'nodes':>7}{'conv':>6}{'resize':>8}{'lin':>5}"
    print(hdr)
    print("-" * len(hdr))
    for r in summary:
        print(f"{r['model']:<40}{r['nodes']:>7}{r['conv_like']:>6}"
              f"{r['resize']:>8}{r['resize_linear']:>5}")
    print("\nLegend: conv=Conv+ConvTranspose count, resize=Resize nodes, "
          "lin=linear/cubic Resize (XNNPACK-blocking).")
    print("Rule of thumb: resize>0 with lin>0  => XNNPACK likely aborts the build.")
    print("               unsupported ops (see per-model)  => CPU fallback, little/no gain.")


if __name__ == "__main__":
    main()
