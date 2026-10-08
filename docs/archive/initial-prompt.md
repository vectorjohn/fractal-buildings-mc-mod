Good evening! I'm starting work on some exploration of algorithms and eventually implementation of a Minecraft (java edition) mod for doing procedurally generated cities. The file in this folder goes into great detail. I'd like to use it
as a starting point for a roadmap, however there are some things I'd like to consider up front:
There is going to be a lot of work coordinating a team of agents working on this. I have another project I'm working on on this machine, and the workflow has worked pretty well there. That project is in /home/agent/agent-mc-mod. In
particular, the defined agents and CLAUDE.md have been pretty effective.
That said, I believe this 'Procedural Buildings Mod — Roadmap.md' file also has some items it would like to include in the CLAUDE.md. Those should be considered.
There may be references to the specific project agent-mc-mod / Kaiju in those other agent defintions. If so those should be made generic.
Also in that project, there was a lot of concern about parallelism, which may be mentioned in the agents or claude files. There was some concern about running too much in parallel. In this project, I want to be sure that we keep parallel
work a priority.
I also want to ensure that we try as much as possible to make use of the `coder` and `assistant` agents where appropriate. Where we may have many agents working together, they should do most of the work, and only invoke lead-coder for
particularly difficult problems.
'Procedural Buildings Mod — Roadmap.md' defines some agent roles. I have added the names of the agents from agent-mc-mod that I'd like you to use. 
testing and maybe small proof of concepts. Main coding should be still done by the coder.
Ultimately, one of the big things I want to maintain is a similar ROADMAP structure that the agent-mc-mod project used. It allowed long running and interruptible workflows. I have read over the 'Procedural Buildings Mod — Roadmap.md'
file, but if it has any good ideas about how to maintain progress, let me know. We just need a ledger keeping track of progress in some form, and the one from agent-mc-mod seems to be working.
Also of note in that project is the flake.nix. I don't want you to copy it verbatim, because we don't know what all we're going to need. However, we know we'll be doing MC development, so probably a lot of the same. Make sure it's clear
in the CLAUDE.md that it is OK (and even required sometimes) to add dependencies there and always work within the nix dev shell (unless you're just doing pure research and writing files).
So yeah, take a look at that doc, the agent-mc-mod repo and agents, and copy over what we'll need here and integrate ideas as needed.
