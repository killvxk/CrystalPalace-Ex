
Tradecraft Garden
=================

The Tradecraft Garden is an open source corpora of in-memory evasion tradecraft, both load and runtime, 
packaged into position-independent capability loaders.

The technical push of this project is to decompose hacking tool tradecraft into simple units of 
interchangeable parts, separate from C2 frameworks. These separated and interchangeable parts are security 
ground truth. It’s a goal of this project to encourage use of security ground truth outputs for:

 * Breach and Attack Simulation
 * Detection Engineering
 * EDR Test and Evaluation
 * Security Testing Exercises

The broader goal of Tradecraft Garden is to model an approach to ground truth security research that:

 * informs the security conversation
 * serves as a public good resource for others to build on
 * can demonstrate ideas, in context, while serving multiple security use cases and communities

The latest Tradecraft Garden files are at:

https://tradecraftgarden.org

Crystal Palace
==============

Tradecraft Garden's position-independent DLL loaders build on Crystal Palace, an open source linker and
linker specialized to the needs of writing position-independent DLL loaders.

Crystal Palace is open source software and is part of the Tradecraft Garden.

https://tradecraftgarden.org/crystalpalace.html

Compiling
=========

Use 'make clean' to clean all of the targets

Use 'make all' to build all of the targets.

Tradecraft Garden projects are built with MinGW32 on Linux

Copyrights
==========

Like all projects, Tradecraft Garden benefits from and is derivative of works and efforts that have come
before it. The following projects and codes were sourced in the development of these sub-projects. 

These projects are copyright their respective developers. All rights reserved.

ReflectiveDLLInjection (Modifications) [BSD license]
Copyright (C) 2013-2025, Rapid7, Inc. and other authors
https://github.com/rapid7/ReflectiveDLLInjection/blob/81cde88bebaa9fe782391712518903b5923470fb/dll/src/ReflectiveLoader.c#L34

ReflectiveDLLInjection [BSD license]
Copyright (c) 2011, Stephen Fewer of Harmony Security (www.harmonysecurity.com)
https://github.com/stephenfewer/ReflectiveDLLInjection

License
=======

Tradecraft Garden projects are:

Copyright (C) 2025 Raphael Mudge, Adversary Fan Fiction Writers Guild (https://aff-wg.org)

Each Tradecraft Garden sub-project is released under a specific license. Most are licensed under the
3-Clause BSD license. See LICENSE.txt within each sub-project's folder.
