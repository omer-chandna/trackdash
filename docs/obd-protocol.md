# OBD protocol boundary

Not implemented in the foundation. Bluetooth Classic RFCOMM is the first planned
transport. Buffer through ELM prompts; serialize commands and test fragmentation,
echoes, multiple ECU responses, malformed hex, timeouts, and disconnects.

Mode 01 initial scope: 04, 05, 0C, 0D, 0E, 0F, 10, 11, 42, 46, 5C; Mode 09 VIN
where supported. Follow supported-PID bitmap continuation bits and retain ECU
identity. A standard PID is not evidence that a specific vehicle supports it.
No manufacturer commands or BLE GATT UUIDs are verified in this repository.
Diagnostics must remain read-only; no coding, actuators, programming or DTC clearing.
