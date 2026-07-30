package com.arc.mpl.hir;

import java.util.List;
import java.util.Objects;

/** A source-level method call with compiler-resolved overload and virtual dispatch targets. */
public record HirMethodCall(
    HirExpression receiver,
    String sourceName,
    List<HirExpression> arguments,
    List<DispatchTarget> dispatchTargets,
    InvocationKind kind,
    MplType type,
    int stringResultAllocationId
) implements HirExpression {
    public HirMethodCall {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(sourceName, "sourceName");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        dispatchTargets = List.copyOf(Objects.requireNonNull(dispatchTargets, "dispatchTargets"));
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(type, "type");
        if (dispatchTargets.isEmpty()) throw new IllegalArgumentException("方法调用必须至少有一个派发目标");
        if (type == ValueType.STRING && stringResultAllocationId < 1) {
            throw new IllegalArgumentException("String 方法调用必须拥有独立结果 allocationId");
        }
        if (type != ValueType.STRING && stringResultAllocationId != 0) {
            throw new IllegalArgumentException("只有 String 方法调用才能拥有结果 allocationId");
        }
    }

    public record DispatchTarget(String runtimeClass, String function) {
        public DispatchTarget {
            Objects.requireNonNull(runtimeClass, "runtimeClass");
            Objects.requireNonNull(function, "function");
        }
    }

    public enum InvocationKind {
        VIRTUAL,
        SUPER_METHOD,
        SUPER_CONSTRUCTOR
    }
}
