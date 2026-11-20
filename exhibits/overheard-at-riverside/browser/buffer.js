// Node's Buffer, as much of it as heart-ts uses, for a browser: bytes from an answer's ArrayBuffer, read back as
// UTF-8 text. esbuild injects it wherever heart-ts names Buffer.
export class Buffer extends Uint8Array {
  static from(data) {
    return typeof data === "string" ? new Buffer(new TextEncoder().encode(data)) : new Buffer(data);
  }

  toString() {
    return new TextDecoder().decode(this);
  }
}
