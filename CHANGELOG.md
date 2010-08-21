### `0.4.3` - August 21, 2010
- switch to clojure 1.2.0
- support restarting of project jvm from clojure using `cake.core/bake-restart` function
- restart project jvm after fetching new deps or compiling new `.class` files
- make `deps` a dependency of `compile`
- automatically detect circular dependencies
- fix bug that was leaving defunct jvms running
- only fetch deps if `project.clj` is newer than a file in `lib` or `pom.xml`
- add force option to compile and deps
- change `clean` to not remove deps; use `clean deps` to remove deps too
- allow `invoke` function to override `*opts*`, though a given task will still only be executed once
- improved debugging messages for java compilation
- improved `release` task now prints clojars [INFO] messages

### `0.4.0` - August 18, 2010
- full Windows support, including repl and readline; [install instructions](http://wiki.github.com/ninjudd/cake/cake-on-windows)
- moved environment-specific vars to `cake` namespace and make them available to all commands (e.g. `cake/*env*`, `cake/*pwd*`)
- move core functionality in `cake.core`
- bind `*command-line-args*` for all commands
- support for stdin in all tasks (except in windows because you cannot do non-blocking IO on non-sockets)
- new `filter` task for creating unix filters (like `perl -e`)
- new `version` to print or change the project version
- `cake --version` now reports the version of cake; shutdown jvms when the version changes
- don't automatically fetch deps unless `lib` directory is empty
- bug fixes in `test` task
- always exclude clojure and clojure-contrib from dev-dependencies (to prevent 1.1/1.2 aot conflicts)
- pass `-d32` and `-client` to java when starting the jvm for improved speed
  (can be disabled with `project.java_opts` and `cake.java_opts` in `.cake/config`)

### `0.3.13` - August 12, 2010
- add `run` task for executing clojure files
- add global project based in `~/.cake`
- switch to `tasks.clj` for auxillary tasks instead of `build.clj`
- repl improvements when pasting code in
- add `--project` and `--global` options for overriding which project cake should use 
- support running clojure shell scripts with `#!/usr/bin/env cake`

### `0.3.8` - August 9, 2010
- initial Windows support
- add warning when building a war if all namespaces aren't being aot compiled
- always print system tasks in help

### `0.3.6` - August 4, 2010
- support `(read)` in repl
- ruby 1.9 compatibility fixes
- support eval when not in a project
- switch swank to use .cake/config for autostart

### `0.3.3` - July 28, 2010
- add `eval` task for executing short code snippets
- clojure 1.1 compatibility fixes
- improved logging and installation type detection

### `0.3.2` - July 26, 2010
- initial public release
- add `autotest` task
- add `:require` and `:startup` options to project.clj for running code at project startup
 
### `0.3.1` - July 8, 2010
- initial private beta