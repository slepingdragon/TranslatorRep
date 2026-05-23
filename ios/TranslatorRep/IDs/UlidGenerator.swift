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
// Runtime library: swift-ulid (oherrala/swift-ulid via SPM — see ios/PACKAGES.md).
// Callers must never reach past this facade; if the library is swapped, only this
// file changes.

import Foundation
import ULID  // swift-ulid: pinned in Story 1.2 via SPM

enum UlidGenerator {

    /// Generate a fresh canonical 26-char Crockford base32 ULID using the current time.
    static func next() -> String {
        return ULID().ulidString
    }

    /// Spec-correct ULID encoding from explicit (48-bit timestamp, 80-bit random).
    /// Library-independent — pure Crockford base32 math.
    ///
    /// The Crockford base32 alphabet is `0123456789ABCDEFGHJKMNPQRSTVWXYZ`
    /// (excludes I, L, O, U to avoid visual ambiguity).
    ///
    /// - Parameters:
    ///   - timestampMs: Unix epoch milliseconds; must fit in 48 bits (≤ 2^48 − 1,
    ///     practically unbounded). Higher bits are masked off.
    ///   - random80BitBigEndian: Exactly 10 bytes (80 bits) of random material in
    ///     big-endian byte order.
    /// - Returns: The canonical 26-character ULID string.
    /// - Throws: `UlidGeneratorError.invalidRandomLength` if the random array is not 10 bytes.
    static func encodeCanonical(timestampMs: Int64, random80BitBigEndian: [UInt8]) throws -> String {
        guard random80BitBigEndian.count == 10 else {
            throw UlidGeneratorError.invalidRandomLength(got: random80BitBigEndian.count)
        }
        guard timestampMs >= 0 else {
            throw UlidGeneratorError.negativeTimestamp(got: timestampMs)
        }

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

enum UlidGeneratorError: Error, CustomStringConvertible {
    case invalidRandomLength(got: Int)
    case negativeTimestamp(got: Int64)

    var description: String {
        switch self {
        case .invalidRandomLength(let got):
            return "ULID random portion must be exactly 10 bytes (80 bits); got \(got)"
        case .negativeTimestamp(let got):
            return "timestampMs must be non-negative; got \(got)"
        }
    }
}
