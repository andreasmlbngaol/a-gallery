#!/usr/bin/env python3
"""
Diagnostic: figure out WHY isnet-general-use won't run below 1024.

It does NOT modify anything. It prints:
  1) how every Resize/Upsample gets its target size (constant vs computed),
  2) a backward trace from the failing Concat node, showing each feeding node's
     op type and its static shape at 1024 -- so we can see exactly which branch
     is stuck at a baked size (e.g. 32) while its sibling scales.

Usage:
    pip install onnx numpy
    python3 tools/inspect_model.py tools/isnet-general-use.onnx
    python3 tools/inspect_model.py tools/isnet-general-use.onnx --node /stage1/Concat_2 --depth 8

Paste the full output back.
"""
import argparse
import sys


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("--node", default="/stage1/Concat_2", help="failing node to trace back from")
    ap.add_argument("--depth", type=int, default=8)
    args = ap.parse_args()

    import onnx

    model = onnx.load(args.input)
    try:
        model = onnx.shape_inference.infer_shapes(model)
    except Exception as e:
        print(f"[warn] shape inference failed: {e}")
    g = model.graph

    # shape map
    shapes = {}
    def rec(vis):
        for vi in vis:
            dims = []
            for d in vi.type.tensor_type.shape.dim:
                dims.append(d.dim_value if d.HasField("dim_value") else "?")
            shapes[vi.name] = dims
    rec(g.input); rec(g.output); rec(g.value_info)

    init_names = {i.name for i in g.initializer}
    # producer map: tensor name -> node
    producer = {}
    for n in g.node:
        for o in n.output:
            producer[o] = n

    def src_kind(name):
        if not name:
            return "(none)"
        if name in init_names:
            return "Initializer"
        p = producer.get(name)
        if p is None:
            return "graph-input"
        return f"{p.op_type}({p.name})"

    # 1) Resize/Upsample summary
    print("\n== Resize/Upsample nodes ==")
    count = 0
    for n in g.node:
        if n.op_type not in ("Resize", "Upsample"):
            continue
        count += 1
        scales = n.input[2] if len(n.input) > 2 else ""
        sizes = n.input[3] if len(n.input) > 3 else ""
        outshape = shapes.get(n.output[0], "?")
        if count <= 12:
            print(f"  {n.name} [{n.op_type}] out@1024={outshape}")
            print(f"      scales <- {src_kind(scales)} ; sizes <- {src_kind(sizes)}")
    print(f"  ... total {count} Resize/Upsample nodes")

    # 2) backward trace from failing node
    print(f"\n== Backward trace from {args.node} (depth {args.depth}) ==")
    target = None
    for n in g.node:
        if n.name == args.node:
            target = n
            break
    if target is None:
        print(f"  node '{args.node}' not found. Concat nodes are:")
        for n in g.node:
            if n.op_type == "Concat":
                print(f"    {n.name} inputs={list(n.input)}")
        return 0

    seen = set()
    def show(name, depth, indent):
        if depth < 0 or name in seen:
            return
        seen.add(name)
        p = producer.get(name)
        sh = shapes.get(name, "?")
        if p is None:
            kind = "Initializer" if name in init_names else "graph-input"
            print(f"{indent}{name}: {kind} shape@1024={sh}")
            return
        print(f"{indent}{name}: <- {p.op_type}({p.name}) shape@1024={sh}")
        for i in p.input:
            show(i, depth - 1, indent + "  ")

    for i, inp in enumerate(target.input):
        print(f"  input[{i}] of Concat:")
        show(inp, args.depth, "    ")

    return 0


if __name__ == "__main__":
    sys.exit(main())
