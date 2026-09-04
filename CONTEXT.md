# Wameed (وميض)

A two-peer LAN file-transfer system: an Android app and a Windows receiver that
discover each other on the local network and exchange files, text, and URLs in
both directions over WebSocket.

## Language

**Wire Protocol**:
The complete contract of what crosses the network between the phone and the PC —
ports, message types, status vocabulary, JSON keys, and the transfer-id format.
Owned by exactly two mirrored modules: `WameedProtocol` (Kotlin) and
`protocol.py` (Python); no wire literal may appear outside them.
_Avoid_: message format, API

**Hello**:
The handshake message either peer sends after opening a WebSocket, identifying
the device and requesting pairing. One canonical form per side; every field is
always sent.
_Avoid_: handshake message, greeting

**Pairing**:
The one-time trust decision a receiving peer makes about a connecting device,
remembered by device id. Produces the statuses `paired`, `pairing_required`,
or `rejected`.
_Avoid_: authentication, login

**Transfer**:
One file moving between peers, identified by a stable transfer id derived from
filename and size (prefix `a2w-` phone→PC, `w2a-` PC→phone) so an interrupted
transfer can resume.
_Avoid_: upload, download

**Transfer Status**:
The frame stream a receiving peer sends back during a transfer:
`ready` (with resume offset), `progress`, `saving`, `saved`, `error`.
_Avoid_: ack (for the whole frame; "ack" is fine for the concept of acknowledging)

**Discovery**:
How peers find each other on the LAN: UDP broadcast pings answered by pongs or
PC announcements on the discovery port. The PC announces as service `wameed_pc`,
the phone as `wameed_phone`.
_Avoid_: scanning, search

**Receiver**:
The Windows-side application (`receiver.py` / Wameed.exe). Despite the name it
also sends — both peers are full senders and receivers.
_Avoid_: server, PC app (in code)

**Keep-Alive Service**:
The Android foreground service that holds a WebSocket open to the PC, pings it,
and hosts the phone's receiving server so transfers work while the app is
backgrounded.
_Avoid_: background service, connection service (as a concept)
