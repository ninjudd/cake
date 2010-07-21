#### Save your fork, there's cake!

Cake is a build tool for Clojure that is as easy to use as it sounds, inspired by fond
memories of Rake and countless hours of singeing my hair with other build tools.

## Installation

There are three ways to get Cake. The simplest method is just to install the gem. If
you're new, that's what we recommend.

### Using gem

1. `gem install cake`

### Standalone script

1. [Download the script](https://github.com/ninjudd/cake/raw/master/bin/cake)
2. Put it somewhere in your path and `chmod +x cake` to make it executable

### Git repository

1. `git clone http://github.com/ninjudd/cake`
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
      :dependencies [[clojure "1.2.0-master-SNAPSHOT"]
                     [clojure-contrib "1.2.0-SNAPSHOT"]
                     [clojure-useful "0.2.1"]
                     [clojure-protobuf "0.3.0"]
                     [tokyocabinet "1.2.3"]])

## Default Tasks

Cake provides default tasks for most of the things you probably do on a regular basis.

    cake help     ;; Print tasks with documentation. Use 'cake help TASK' for more details.
    cake test     ;; Run project tests.
    cake compile  ;; Compile all clojure and java source files.
    cake deps     ;; Fetch dependencies and create pom.xml.
    cake jar      ;; Build a jar file containing project source and class files.
    cake release  ;; Release project jar to clojars.
    cake install  ;; Install jar to local repository.
    cake war      ;; Create a web archive containing project source and class files.
    cake uberjar  ;; Create a standalone jar containing all project dependencies.
    cake uberwar  ;; Create a web archive containing all project dependencies.
    cake repl     ;; Start an interactive shell.
    cake swank    ;; Report status of swank server and start it if not running.

## Custom Tasks

You can also create your own custom tasks using the `deftask` macro. Just add your tasks
directly to project.clj or build.clj, or if you put your tasks in a namespace within your
src directory they can be used by both your project and other projects. In this case, you
just need to add the enclosing namespace to the `:tasks` vector in project.clj.

Like many build tools, Cake uses a dependency-based programming style. This means you
specify the tasks your task depends on and Cake will run those tasks before running your
task, ensuring that each task is only run once. For more details, check out Martin Fowler's
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

Dependencies are specified as a set to indicate that they can be run in any order. This
means that in the `test` task above, you can't be sure that `compile` will run before
`data-load`. If `data-load` depends on `compile`, you have to add the dependency
explicitly. This gives us greater flexibility in the future to optimize performance by
running tasks in parallel.

## Command-line Arguments

There is no way to pass parameters from one task to another, however, Cake does parse all
command-line arguments and make them available to all tasks as a var called `opts` which
contains a map of keys to vectors of repeated values. Named args begin with `--keyname`
and are mapped to `:keyname`. Unnamed arguments are mapped to `:taskname`. Repeated named
values can be specified by repeating a key or by using commas in the value.  Single and
double dashes are both supported though a single dash followed by word characters without
internal dashes or an equal sign is assumed to be single character argument flags and are
split accordingly.

Here are some example Cake commands followed be the corresponding opts:

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
separating them with commas, as in the third example.

## Advanced Techniques

### Extending tasks

Like Rake, Cake allows you to add actions, dependencies and even documentation to existing
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

## Inspirational Quote

> You are what makes Clojure great - find some cake and celebrate!
>
> &mdash; [Rich Hickey](http://clojure.blogspot.com/2009/10/clojure-is-two.html) (taken totally out of context)
