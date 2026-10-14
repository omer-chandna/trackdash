# OBD protocol and adapter evidence

Standard Mode 01 polling discovers supported PID bitmaps per ECU, follows bitmap
continuations, and requests only supported dashboard PIDs. Supported definitions:
04 load, 05 coolant, 0C RPM, 0D speed, 0E ignition timing, 0F intake, 10 MAF,
11 throttle, 42 module voltage, 46 ambient and 5C oil temperature. Values retain
canonical units and acquisition timestamps. Temperature 5C remains unavailable
when unsupported; gear and oil-pressure switch are unavailable in generic OBD.

Initialization: ATZ, ATE0, ATL0, ATS0, ATH1, ATSP0. Headers remain enabled to avoid
mixing ECU replies. The current default selects the first sorted responding ECU.
CAN single frames and ISO-style headers are parsed; malformed hex/truncation are
rejected. CAN VIN 0902 assembly validates ECU, length, sequence and VIN characters;
other VIN framing is explicitly unavailable. ATI and ATDP expose identification.

Commands are serialized. Desired intervals range from 100 ms RPM to 2 s ambient/
voltage; real rates depend on the adapter/vehicle latency. Readings older than 5 s
are marked stale. The scheduler waits for each response and backs off/reconnects
on failures, exhausting three consecutive connection/poll failures. Successful
reads reset retries. Diagnostics show latency, counts, errors, supported PIDs,
firmware, protocol and VIN status. No clearing/coding/service-write commands exist.

## Owner's adapter: Veepeak OBDCheck BLE

[Veepeak specifications](https://veepeak.com/products/obdcheck-ble) document both
Classic Bluetooth and BLE. [Official Android setup](https://veepeak.com/pages/obdcheck-ble)
uses Classic Bluetooth with VEEPEAK paired using PIN 1234. TrackDash uses Android
RFCOMM SPP (00001101-0000-1000-8000-00805f9b34fb) for this connection. Socket calls
run on a dedicated worker; cancellation/timeout closes the socket to unblock IO.
The owner identified Amazon ASIN B073XKQQQW on 2026-10-13. No physical captures
or achieved poll rates from that adapter have yet been provided.

## Experimental BLE driver

[OBDLink CX developer notes](https://support.obdlink.com/support/solutions/articles/43000746707-obdlink-cx-adapter-notes)
document service FFF0, notification FFF1, write FFF2, subscription-triggered pairing
and acknowledged serial writes. TrackDash scans for CX-named devices, verifies
these characteristics, enables CCCD notifications, serializes acknowledged writes,
and buffers fragmented notifications to the ELM prompt. Commands fit the default
20-byte payload; no larger MTU or queued writes are assumed. GATT operations time
out and close resources; notification storage is bounded. This is CX-specific,
not a claimed universal BLE driver, and remains hardware-unverified.

## Parser references and tests

[Elm Electronics ELM327DSL](https://elmelectronics.com/wp-content/uploads/2020/05/ELM327DSL.pdf),
pages 44–46, documents headers, multiline responses and ISO-TP framing. Its example
VIN is used in synthetic fixtures expanded to ATH1 framing; these are not captures
from the owner's car. Tests cover standard scaling, capability maps, multiple ECUs,
malformed/truncated input, VIN sequence/length, fragmented prompts, size bounds and
command restrictions. Android radio/pairing timing requires device tests.
