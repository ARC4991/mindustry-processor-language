package com.arc.mpl.profile;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Built-in profiles. Profile data will later be generated from audited game sources. */
public final class KnownProfiles {
    private static final TargetProfile V146 = new FixedProfile(
        "v146",
        Set.of("baseline-logic", "unit-bind-cycle"));
    private static final TargetProfile V159_7 = new FixedProfile(
        "v159.7",
        Set.of("baseline-logic", "unit-bind-cycle", "select", "printchar", "format", "unpackcolor", "draw-print",
            "logic-build-variables"));

    private KnownProfiles() {
    }

    public static Optional<TargetProfile> find(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "v146" -> Optional.of(V146);
            case "v159.7" -> Optional.of(V159_7);
            default -> Optional.empty();
        };
    }

    private record FixedProfile(String id, Set<String> capabilities) implements TargetProfile {
        @Override
        public int memoryCellCapacity() {
            return 64;
        }

        @Override
        public int memoryBankCapacity() {
            return 512;
        }

        @Override
        public int instructionsPerTick(ProcessorKind processor) {
            return switch (processor) {
                case MICRO -> 2;
                case LOGIC -> 8;
                case HYPER -> 25;
            };
        }

        @Override
        public int maxInstructions() {
            return 1_000;
        }

        @Override
        public int maxJumpLabels() {
            return 500;
        }

        @Override
        public int maxTokensPerStatement() {
            return 16;
        }

        @Override
        public int maxGraphicsBufferCommands() {
            return 256;
        }

        @Override
        public int displayFlushCommandLimit() {
            return 1_024;
        }

        @Override
        public int maxMessageUtf16CodeUnits() {
            return 400;
        }

        @Override
        public int maxDrawCoordinateMagnitude() {
            return 1_023;
        }
    }
}
