// UlidGenerator.swift
// TranslatorRep — Story 1.5
//
// Canonical ULID generator. All entity IDs (Pair.id, Call.id, Utterance.id,
// Caption.id, MessageId in Data Channel payloads) flow through this enum. Output
// is a 26-character Crockford base32 string, time-sortable and collision-resistant
// at 2-user scale.
//
// See architecture §4 (ID Format — Locked Globally) and
// /shared/canonical-names.md §3. Cross-platform parity with the Android version is
// verified by UlidParityTests against the locked test vector — both platforms must
// produce byte-identical output from the same (timestamp, random) input.
//
// Runtime library: yaslab/ULID.swift via SPM (module name `ULID`) — see
// ios/PACKAGES.md. The import is gated by `#if canImport(ULID)` so this file
// compiles even before Story 1.2 wires the SPM dependency (in that pre-wire
// state, `next()` traps at runtime but `encodeCanonical(...)` works — the
// parity test exercises only the latter).
//
// Callers must never reach past this facade; if the library is swapped, only
// this file changes.

import Foundation
#if canImport(ULID)
import ULID
#endif

enum UlidGenerator {

    /// Generate a fresh canonical 26-char Crockford base32 ULID using the current time.
    ///
    /// Pre-SPM-wire-up (before Story 1.2 close-out), this traps with a clear message —
    /// `encodeCanonical(...)` is the library-independent path used by the parity test.
    static func next() -> String {
        #if canImport(ULID)
        return ULID().ulidString
        #else
        preconditionFailure(
            "UlidGenerator.next() requires the ULID SPM dependency — wire it in Story 1.2 per ios/PACKAGES.md."
        )
        #endif
    }

    /// Spec-correct ULID encoding from explicit (48-bit timestamp, 80-bit random).
    /// Library-independent — pure Crockford base32 math. Mirrors Android's
    /// `UlidGenerator.encodeCanonical(...)` exactly (non-throwing; traps on
    /// contract violation via `precondition` to match Kotlin's `require {}`).
    ///
    /// The Crockford base32 alphabet is `0123456789ABCDEFGHJKMNPQRSTVWXYZ`
    /// (excludes I, L, O, U to avoid visual ambiguity).
    ///
    /// - Parameters:
    ///   - timestampMs: Unix epoch milliseconds. Must be in `[0, 2^48 − 1]` —
    ///     values outside this range trap.
    ///   - random80BitBigEndian: Exactly 10 bytes (80 bits) of random material
    ///     in big-endian byte order.
    /// - Returns: The canonical 26-character ULID string.
    static func encodeCanonical(timestampMs: Int64, random80BitBigEndian: [UInt8]) -> String {
        precondition(
            random80BitBigEndian.count == 10,
            "ULID random portion must be exactly 10 bytes (80 bits); got \(random80BitBigEndian.count)"
        )
        precondition(timestampMs >= 0, "timestampMs must be non-negative; got \(timestampMs)")
        precondition(
            timestampMs <= maxTimestampMs,
            "timestampMs must fit in 48 bits (≤ \(maxTimestampMs)); got \(timestampMs)"
        )

        // Lay out 128 bits as a 16-byte buffer: 6-byte big-endian timestamp || 10-byte random.
        var buf = [UInt8](repeating: 0, count: 16)
        for i in 0..<6 {
            buf[5 - i] = UInt8((timestampMs >> (i * 8)) & 0xFF)
        }
        for i in 0..<10 {
            buf[6 + i] = random80BitBigEndian[i]
        }

        // Encode 128 bits as 26 chars of Crockford base32 over the 130-bit space
        // (leading char carries only the top 2 bits of the 128-bit value — i.e. the
        // remaining 3 bits come from the top of buf[0]).
        let alphabet: [Character] = Array("0123456789ABCDEFGHJKMNPQRSTVWXYZ")
        var out: [Character] = []
        out.reserveCapacity(ulidLength)
        for i in 0..<ulidLength {
            let bitOffsetFromLeft128 = (i * 5) - 2
            let idx = extract5BitsAt(buf: buf, bitOffsetFromLeft: bitOffsetFromLeft128)
            out.append(alphabet[idx])
        }
        return String(out)
    }

    private static let ulidLength = 26
    private static let maxTimestampMs: Int64 = (1 << 48) - 1

    /// Extract 5 bits from `buf` starting at `bitOffsetFromLeft` (counting from the
    /// MSB of `buf[0]`). A negative offset is allowed and corresponds to the prepended
    /// zero-padding of the ULID's 130-bit encoding space.
    private static func extract5BitsAt(buf: [UInt8], bitOffsetFromLeft: Int) -> Int {
        var value = 0
        for bitIndex in 0..<5 {
            let absoluteBit = bitOffsetFromLeft + bitIndex
            let bit: Int
            if absoluteBit < 0 {
                bit = 0
            } else {
                let byteIndex = absoluteBit >> 3
                let bitInByteFromMsb = absoluteBit & 0x07
                bit = (Int(buf[byteIndex]) >> (7 - bitInByteFromMsb)) & 0x01
            }
            value = (value << 1) | bit
        }
        return value
    }
}
