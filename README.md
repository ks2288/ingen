### Overview

Ingen is a Kotlin library specifically for implementing a more-standardized 
way of using Java's native `ProcessBuilder` package. This library wraps 
`ProcessBuilder.Process` instances in a way that supports the launching and 
sand-boxing of language-agnostic subprocesses in a thread-safe, asynchronous, 
and reactive manner. Output from these arbitrary subprocesses supports 
standardization via the use of Standardized Protocol (Jitpack link: 
[![](https://jitpack.io/v/ks2288/StandardizedProtocol.svg)](https://jitpack.io/#ks2288/StandardizedProtocol)).

### A Bit of History
A few years ago, I realized the personal need for a tool that essentially acted 
as a language-agnostic test harness that could run and manage modular, 
standalone code that served some purpose within a multilayered, full-stack 
system but whose origin was assumed to be completely unrelated from one another.

That's how it started. I had libraries of (generally C or C++) code that were 
used successfully to mock generic RF communications using any one of myriad 
capable development boards (the nRF52xxx line, specifically). But those dev 
boards weren't systems in the true sense. They had no userland-oriented, 
configurable operating systems like we're used to in Linux/Unix/macOS/Doze 
(or even something like Docker, which is really just a VM PaaS), so they 
couldn't do the automation on their own without a hefty addition to their own 
firmware code. Even then, that would have been a superfluous task; the 
result wasn't scalable if I needed test harness code for another RF 
protocol/device combination like LoRa. 

In came `ProcessBuilder`. Our dev machines were M1 Macbooks, so anything JRE 
was more than fair game. The focus became generic code that ran on a 
certain chipset, and these chipsets were chosen based on the relative 
prevalence of dev kits/dongles that support them and their modular use on 
any given system. Now, we had arbitrary host devices that required nothing 
but the JRE that ran code that was itself conformant to a known, universal 
protocol. The packet structure became known, and the specific needs of the test 
devices running the code became as arbitrary as it could be. It's certainly 
a lot more efficient traveling abroad and speaking a known language with 
denizens of other worlds, isn't it? At that point, the specific people 
communicating doesn't matter because the communication structure is known.

The name is short for the Latin class of words whose root stems from "ingen-"
such as "ingenuus" meaning "capable" or "resourceful". Or, depending on your
opinion of the idea behind this project, it could very well stand for
"ingenuum" meaning "naive" or "unimaginative" :-). It is also a pun of sorts
derived from "engine", as in an engine for executing and managing any
program you can feasibly write for a host system. Have a great Python BLE
library for your Raspberry Pi, and you want to use it for another program
written in C that acts on the data coming in through BLE, maybe from an
environmental etc. sensor like a SenseHat? Fantastic. Ingen can run all of
those sovereign programs/scripts simultaneously, and it also at the same time
handles their output via the oldest communication interface known to the modern
software engineer: `STDIO`. Just as Java's `ProcessBuilder` is meant
to be used.

### Potential Uses

Ingen will not tell you what you should do with the output of the processes 
that it sand-boxes, obviously. That is up to your needs. For example, maybe you 
just want something that can execute modular sensor code on a Raspberry Pi, and 
you want all that output piped back to the Kotlin/Java app you're writing that 
shows a display screen. And that screen shows you things like accelerometer or 
hydrometer data in graphical format. Give Ingen the location of your standalone
files/scripts and the system locations where those programs live (programs 
as in your local Python interpreter and its path), and you're in business. 
Ingen is configured using simple JSON text files that, when initialized will 
live in a runtime directory of your choosing and can be edited at any time, 
and require only a simple restart (NOT a system restart) to take effect. 
This code also includes templates for gleaning the structure of the 
configuration files - and even some tools to aid in file system 
initialization (which is really just directory creation according to the 
config file definitions within the code).

As an example, imagine having Python code that's used to mock the GATT table
of a proprietary BLE peripheral. It doesn't matter what that code is talking
to over BLE - it just matters that you have a system that can run the code. So,
you realize that you also need to automate that BLE functionality to mimic
certain conditions under which you're testing something that's talking to
the BLE module that this Python code is standing in for. And that something
that's being tested is a device your company is manufacturing that has a
design verification test (DVT) written that defines those conditions that need
tested. The thing you're mocking that talks to your company's device via BLE
cannot be procured en masse, so you need something on the manufacturing line
that can quickly audit certain aspects of the device's performance that might
otherwise render the product nonviable when it's expected to be talking to
this other company's device. Using Ingen, you can have a JRE-based
"hypervisor" that acts as the conduit through which the execution of
otherwise-unrelated code comes together to achieve some goal, in this example
case being the passing of the DVT.

No idea what any of that means? Then you only have one less potential
categorical use for the functionality Ingen provides. The potential uses are
limited only by your imagination. I use it extensively for outfitting 
Raspberry Pi units with what many consider "smart" functionality. Think 
wireless control of things like motors that actuate hidden wall safe 
compartment doors.

### Functionality

Using `ProcessBuilder` to execute system code from within the JRE is not a 
novel concept.

Where Ingen becomes handy is in its organization and asynchronicity. 
Launching long-running processes is made simple though Ingen's ability to 
leverage both Coroutines and RxJava in support of behaviorally-based 
application implementations. The launching of asynchronous programs and 
handling their output through both coroutine channels/flows and Rx 
publishers is what Ingen was built for. Similarly, Ingen provides the RxKotlin 
dependencies as an API, so instantly transforming a `Flowable` into a `Flow` 
and vice versa is as simple as a dot operator throughout the rest of your code.

More specifically, Ingen supports the execution of any other executable 
script/app/file on the host system whose requisite permissions are identical 
to a standard user role carrying the same abilities. I.e., if your Linux 
user has the "x" or "execute" category of necessary permissions and can 
otherwise run the file (or can at least be configured to do so), so too can any 
app running this library that was launched by your user under normal runtime 
circumstances.

The output from all sand-boxed subprocesses launched via Ingen

More instructions to come in V1.2.0