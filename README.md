Cake is a tasty build tool for clojure, designed to be as powerful and fun to use as clojure itself.
Cake has the following features:

* persistent JVM, eliminating start-up overhead
* dependency based task model, making it simple to define your own tasks and extend existing ones
* large library of built in tasks
* compatibile with most Leiningen project.clj files
* cross platform (Linux, OS X, Windows, Commodore 64)
* enhanced concurrent REPL with command history, paren matching and tab completion

_This software is a pre-release, please submit an issue on github if you run into any problems._

## Installation

There are three ways to get Cake. The simplest method is just to install the gem. If
you're new, that's what we recommend.

### Using gem

1. `gem install cake`

### Standalone script

1. [Download the script](http://github.com/ninjudd/cake-standalone/raw/master/cake)
2. Put it somewhere in your path and `chmod +x cake` to make it executable

### Git repository

1. `git clone git://github.com/ninjudd/cake.git`
2. Symlink bin/cake into your path and make it executable

## Getting Started

Cake is compatible with Leiningen project.clj files, so if you already have a project.clj,
you're ready to go. Just install Cake and then type `cake` in your project root for a list
of tasks.

If you don't yet have a project.clj file, creating one is simple. Here's an example:

    (defproject jiraph "0.2.7"
      :description "Embedded graph db library for Clojure."
      :url "http://jiraph.org"
      :tasks [protobuf.tasks]
      :dependencies [[clojure "1.2.0"]
                     [clojure-contrib "1.2.0"]
                     [clojure-useful "0.2.1"]
                     [clojure-protobuf "0.3.0"]
                     [tokyocabinet "1.2.3"]])

## Default Tasks

Cake provides default tasks for most of the things you probably do on a regular basis.

    cake help      ;; Print tasks with documentation. Use 'cake help TASK' for more details.
    cake deps      ;; Fetch dependencies and create pom.xml.
    cake clean     ;; Remove cake build artifacts.
    cake repl      ;; Start an interactive shell with history and tab completion.
    cake run       ;; Execute a script in the project jvm.
    cake test      ;; Run project tests.
    cake autotest  ;; Automatically run tests whenever your project code changes.
    cake compile   ;; Compile all clojure and java source files.
    cake jar       ;; Build a jar file containing project source and class files.
    cake uberjar   ;; Create a standalone jar containing all project dependencies.
    cake bin       ;; Create a standalone console executable for your project.
    cake install   ;; Install jar to local repository.
    cake release   ;; Release project jar to clojars.
    cake deploy    ;; Deploy war to a group of servers.
    cake upgrade    ;; Upgrade cake to the most current version.
    cake eval      ;; Eval the given forms in the project JVM.
    cake filter    ;; Thread each stdin line through the given forms, printing the results.
    cake war       ;; Create a web archive containing project source and class files.
    cake uberwar   ;; Create a web archive containing all project dependencies.
    cake swank     ;; Report status of swank server and start it if not running.
    cake version   ;; Print the current project name and version.

Cake also provides several system tasks for managing the persistent JVM.

    cake start     ;; Start cake jvm processes.
    cake stop      ;; Stop cake jvm processes.
    cake restart   ;; Restart cake jvm processes.
    cake reload    ;; Reload any .clj files that have changed or restart.
    cake ps        ;; List running cake jvm processes for all projects.
    cake kill      ;; Kill running cake jvm processes. Use -9 to force or --all for all projects.

[Default Task Documentation](http://wiki.github.com/ninjudd/cake/default-tasks)

## Custom Tasks

Custom tasks are created using the `deftask` and `defile` macros in either project.clj, 
tasks.clj or within your src directory. Any namespaces within src containing tasks will need to 
be added to the `:tasks` vector in project.clj and will be usable by other projects.

Like many build tools, Cake uses a dependency-based programming model. This means that if other tasks your task is dependent on share a dependency, that dependency will only be ran once. For more details, check out Martin Fowler's
[excellent article](http://martinfowler.com/articles/rake.html#DependencyBasedProgramming)
on Rake. Here is the example from that article using Cake syntax:

    (deftask code-gen
      "This task generates code. It has no dependencies."
      (println "generating code...")
      ...)

    (deftask compile #{code-gen}
      "This task does the compilation. It depends on code-gen."
      (println "compiling...")
      ...)

    (deftask data-load #{code-gen}
      "This task loads the test data. It depends on code-gen."
      (println "loading test data...")
      ...)

    (deftask test #{compile data-load}
      "This task runs the tests. It depends on compile and data-load."
      (println "running tests...")
      ...)

Dependencies for a task are denoted by a set. For the `deftask` macro, dependencies are tasks that should be invoked before the task being defined. 

### File Tasks

The `defile` macro is used to define file generation tasks. Instead of a symbol, the task is named by a string that is the path to the file relative to the project root. Dependencies of `defile` tasks are essentially when clauses that say to invoke the file task only if any of the dependencies have changed since the last time the file was generated.

    (defile "lib/deps.clj" #{"project.clj"}
      "This task is only ran if project.clj is newer than lib/deps.clj"
      (println "generating lib/deps.clj from project.clj...")
      ...)

You can also mix file and task dependencies for both macros. Within the dependency set, strings represent file generation tasks and symbols represent regular tasks.

    (deftask uberwar #{"web.xml" compile}
      "This appends a condition to the task. `uberwar` will only be ran if web.xml was touched, or compile was ran, since `uberwar` was last invoked.")

TODO: Re-opening task documentation
TODO: :when clause documentation

## Command-line Arguments

There is no way to pass parameters from one task to another, however, cake does parse all
command-line arguments and make them available to all tasks as a var called `*opts*` which
contains a map of keys to vectors of repeated values. Named args begin with `--keyname`
and are mapped to `:keyname`. Unnamed arguments are mapped to `:taskname`. Repeated named
values can be specified by repeating a key or by using commas in the value.  Single and
double dashes are both supported though a single dash followed by word characters without
internal dashes or an equal sign is assumed to be single character argument flags and are
split accordingly.

Here are some example cake commands followed be the corresponding values of `*opts*`:

    cake help compile
    {:help ["compile"]}

    cake test :unit :functional foo.test.login-controller
    {:test [":unit" ":functional" "foo.test.login-controller"]}

    cake compile --compile-native=x86,debug --verbose
    {:compile-native ["x86", "debug"] :verbose [""]}

    cake foo -vD -no-wrap -color=blue,green --style=baroque -color=red
    {:style ["baroque"], :color ["blue" "green" "red"], :no-wrap [""], :D [""], :v [""]}

In the first two examples, you can see that unnamed arguments are placed under the task
name in the opts map. This means you can pass "unnamed" arguments to a task that is a
dependency of the one you are running by adding the task name before the arguments and
separating them with commas.

You can also destructure `*opts*` directly in your task definitions:

    (deftask test #{compile}
       "Run the tests specified on the command line."
       {test-names :test [verbose] :verbose}
       ...)

    (deftask foo #{bar}
       "This task takes a bunch of opts."
        {colors :color [no-wrap] :no-wrap [d] :D [v] :v
         {style 0 :or {style "modern"}} :style}
        ...)

## Advanced Techniques

### Extending tasks

Cake allows you to add actions, dependencies and even documentation to existing
tasks. For example:

    (deftask compile #{compile-native}
      "Native C code will be compiled before compiling Clojure and Java code.")

    (deftask test
      (println "Running integration tests...")
      ...)

Actions will be run in the order they are added, so if you extend Cake default tasks, your
code will be run after the default code. All dependencies will be run before all actions,
but there are no other guarantees about the order dependecies will be run.

### Redefining a task

Sometimes you need to redefine a default task completely. In this case, you can use `undeftask`.

    (undeftask release)
    (deftask release
      "Release code to production servers."
      (println "Releasing to production...")
      ...)

You can also use the :exclude option with the :tasks attribute in project.clj to prevent
tasks from being defined in the first place.

### Manually calling a task

If you have a conditional dependency or need to dynamically execute a task within another
task for some other reason, you can use `invoke`.

    (deftask primary
       (println "Executing primary task...")
       (when (:secondary opts)
          (invoke secondary))
       ...)

    (deftask secondary
       (println "Executing secondary task...")
       ...)

### Native Library Dependencies

Cake will automatically extract precompiled native libraries for your os and architecture
from dependency jars and put them in `lib/native/` and `lib/dev/native/`. Native libraries
must be located in `native/<os-name>/<os-arch>/` within the jar.

    os-name -> linux | macosx | solaris | windows
    os-arch -> x86_64 | x86 | arm | sparc

Cake also adds these directories to `java.library.path` when starting the JVM. If you want
to add additional paths to `java.library.path`, you can add Java properties called
`cake.library.path` and `project.library.path` to `.cake/config`.

### Subproject Dependencies

Sometimes one or more of your dependencies are other projects you are working on, and you
want to track changes to those projects without having to release them to clojars. To do
this, simply add a Java property named `subproject.<project-name>` with the path to the
git checkout to `.cake/config`, like this:

    subproject.clojure-useful   = /Users/justin/projects/useful
    subproject.clojure-complete = /Users/justin/projects/complete

Now instead of fetching these projects from clojars, `cake deps` will run `cake jar` in
each project checkout and copy the resulting jar along with all deps into your main
project's `lib` directory. You still have to run `cake deps` for subproject changes to
show up in your main project, but this is probably best in most cases.

If you really do want changes to clojure source files to show up immediately, you can always
add the subproject `src` directory to your project classpath in `.cake/config` like this:

    project.classpath = /Users/justin/projects/useful/src

## A Persistent JVM

If you've used the JVM for much time at all, you know that one of the worst things about
it is the incredibly slow start-up time. This is even more apparent when you are running a
build tool. Cake solves this problem by using persistent JVMs (one for Cake itself, and
one for your project). When you run the `cake` script, it first makes sure the JVM is
running, then it connects using a socket and sends your command. This makes interacting
with your Clojure project blazingly fast. This also makes it really easy to open multiple
REPL threads that all share a single JVM which is great for testing parallel code.

Cake tries to keep the persistent JVMs running as long as possible by reloading Clojure
files that have changed. However, when .java, .class and .jar files change, Cake has to
restart the project JVM. If you have existing REPL or Swank connections though, Cake will
refuse to close the JVM, printing a warning instead.

### Custom JVM Options

If you need custom command-line options for your JVMs, you can use the `JAVA_OPTS`
environment variable for the project JVM and `CAKE_JAVA_OPTS` for the Cake JVM. You can
also specify options for an individual project by adding the Java properties
`cake.java_opts` and `project.java_opts` to `.cake/config`. For example:

    project.java_opts = -Xms1024M -Xmx2048M -Dfoo=bar
    cake.java_opts    = -Xms128M -Xmx128M -Dfoo=baz

## The global project

If you run cake outside of a project directory, it will use the _global project_.  You can
also use the `--global` option to run any command in the global project, no matter what
your current directory is. The global project is automatically created in
`~/.cake/project.clj` the first time you use it.

_So why do you need the global project?_

It's useful for experimenting with clojure in a repl. You can add `:dependencies` to this
project if you want to experiment with a new library. And any `:dev-dependencies` in the
global project will be available in every project, though you have to run `cake deps
--global` manually when you change `~/.cake/project.clj`. Also, any configuration options
in `~/.cake/config` and any tasks in `~/.cake/tasks.clj` will be available in every
project.

For example, you could put subproject delcarations or JVM options in `~/.cake/config` and
commonly used tasks in `~/.cake/tasks.clj`. Suppose you want to always run tests before
releasing any project. Just add this line to `~/.cake/tasks.clj`:

    (deftask release #{test})

Another cool thing the global project enables is writing clojure shell scripts. Just add
the following line to the top of a file, make it executable and executing it will run the clojure
code in the global project. The file doesn't even have to end in .clj.

    #!/usr/bin/env cake

## Contributors (in order of appearance)

- Justin Balthrop ([ninjudd](http://github.com/ninjudd))
- Lance Bradley ([lancepantz](http://github.com/lancepantz))
- Anthony Simpson ([Raynes](http://github.com/Raynes))
- David Santiago ([davidsantiago](http://github.com/davidsantiago))