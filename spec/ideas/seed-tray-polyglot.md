# Idea: seed trays in four languages, one ARTIE

Status: idea, nothing built. Researched 13 October 2026.

## The demo

A shelf of seedling trays. Each tray has one capacitive soil moisture sensor and a small device that serves the tray as a HEART resource over CoAP. Each device is built in a different language. A hub serves a dashboard over HTTP, and its representation links to each tray with a `coap://` link.

ARTIE enters at the hub's bookmark over HTTP, follows the links, and talks CoAP to each tray. It doesn't know or care what each tray is built in. Every tray offers the same thing: its state, its links, and a "water" act only while the soil is dry.

The point: the protocol and the media type are the contract, and each implementation is free. That is Fielding's first and third rules, on a shelf.

| Tray | Device | Language and runtime | Protocol |
|---|---|---|---|
| 1 | ESP32 | C# on .NET nanoFramework | CoAP |
| 2 | Raspberry Pi Pico W | TypeScript on the Moddable SDK | CoAP |
| 3 | Raspberry Pi Zero 2 W | Kotlin, as a GraalVM native image or Kotlin/Native `linuxArm64` | CoAP, through `heart-coap` |
| Hub | Pi 4 or 5, or a laptop | Kotlin on the JVM | HTTP |

## Hardware per tray

- One capacitive soil moisture sensor (v1.2 or v2.0), about $2 to $4. Not the resistive two-prong kind, which corrodes. The probe is about 10 cm by 2 cm: too big for one small cell, so put one per tray in a representative cell.
- A Pico W reads up to three sensors on its analog inputs. A Pi Zero 2 W has no analog inputs, so it needs an ADS1115 converter (about $5 to $10).
- Later: a small 5 V pump and a relay or MOSFET. Low voltage only, and an adult does the pump wiring.

## What runs where, as researched

**C# / .NET.** .NET nanoFramework has firmware for the Pico W (`RP_PICO_W_RP2040`) and for many ESP32 boards. The RP2040 port is early; the ESP32 port is mature. A CoAP library exists (CoAPSharp for nanoFramework) but has few downloads and is unproven.

**TypeScript.** The Moddable SDK runs on the Pico W and ESP32 and supports TypeScript. Its engine needs under 32 KB of RAM. It has no CoAP module, but it has UDP sockets, and CoAP's basic message format is small enough to write.

**Java.** pico-jvm runs CLDC 1.1 on the Pico in about 270 KB of flash and 18 KB of RAM plus heap, at an early stage. MicroEJ is mature on ESP32 but commercial.

**Kotlin.** No proven route onto a microcontroller today:
- Kotlin/Native needs an operating system. A bare-metal Cortex-M0 target was proposed, not built. The ESP32's Xtensa core has no LLVM backend.
- Kotlin/JS output carries a large runtime; one attempt on Espruino failed for size. Untried on Moddable, and worth one experiment with aggressive dead-code removal.
- Kotlin/Wasm needs WebAssembly GC. WAMR runs on ESP32, but its GC support on that class of chip is unconfirmed.
- TeaVM compiles JVM bytecode, Kotlin included, to C, but its runtime assumes POSIX or Windows.

Kotlin fits one size up, on the Pi Zero 2 W (Linux, ARM64, 512 MB). A GraalVM native image needs an operating system, so it cannot go on a Pico.

## What we'd have to build

- A small HEART responder in each tray's language: state, links, a forms link, the water act with its schema, an ETag, and refusals as problem details.
- A CoAP layer for the Moddable tray, and a check of CoAPSharp for the nanoFramework tray.
- For the Kotlin tray: a Spring-free `heart-coap` service (Californium, heart-core, kotlinx serialization), built as a native image in an arm64 Linux container. Californium under native image is untested and may need reachability metadata from the tracing agent.
- A test that ARTIE follows an HTTP representation's `coap://` link mid-session. Bindings are chosen by URI scheme in `ArtieClient`, so it should work, but that exact jump hasn't been tested.

## Order

1. The Pi Zero 2 W tray in Kotlin, the closest to our code. First proof needs no hardware: build the native image and measure size, start time and memory.
2. The Pico W tray in TypeScript on Moddable.
3. The ESP32 tray in C# on nanoFramework.
4. Separately: one Kotlin/JS-on-Moddable experiment.

## Sources

- nanoFramework, Raspberry Pi Pico: <https://docs.nanoframework.net/content/rpipico/index.html>
- nanoFramework, ESP32 targets: <https://docs.nanoframework.net/content/reference-targets/esp32.html>
- nanoFramework RP2040 port status: <https://www.hackster.io/news/matheus-castello-s-port-brings-net-nanoframework-to-the-raspberry-pi-pico-more-rp2040-based-boards-66863d5b3cc6>
- CoAPSharp for nanoFramework: <https://packages.nuget.org/packages/Idaax.nanoFramework.CoAPSharp>
- Moddable on Pico: <https://github.com/Moddable-OpenSource/moddable/blob/public/documentation/devices/pico.md>
- Moddable network modules: <https://github.com/Moddable-OpenSource/moddable/tree/public/modules/network>
- Moddable XS memory: <https://www.infoq.com/news/2018/11/moddable-iot-javascript-engine/>
- pico-jvm: <https://github.com/orenskl/pico-jvm>
- Kotlin/Native embedded ARM: <https://slack-chats.kotlinlang.org/t/5114093/does-kotlin-native-support-embedded-arm-targets-i-m-guessing>
- Kotlin/Native and ESP32: <https://slack-chats.kotlinlang.org/t/530310/is-it-possible-to-use-kotlin-native-for-esp32-or-similar-chi>
- Kotlin/JS on Bangle.js: <https://discuss.kotlinlang.org/t/running-kotlinjs-on-bangle-js-opensource-smartwach/16831>
- WAMR on ESP32: <https://github.com/anoopelias/hello-wasm-esp32>
- TeaVM C backend: <https://teavm.org/docs/c-backend/getting-started.html> and <https://github.com/konsoletyper/teavm/discussions/770>
- GraalVM on Linux: <https://www.graalvm.org/dev/getting-started/linux/>
