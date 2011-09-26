`cake` is a Clojure development environment for your command line. It is a build tool, a repl, a
package manager, a script launcher, and a deployment tool mixed together and baked into a single,
delicious command, `cake`.

You can use `cake` with any editor, but it goes especially well with [emacs](http://github.com/flatland/cake/wiki/emacs),
[vi](http://github.com/flatland/cake/wiki/vi), and [textmate](http://github.com/flatland/cake/wiki/textmate).
`cake` is cross-platform. It works on OS X, Linux and Windows. Also, unlike most other JVM-based
command line tools, it is fast!

## Installation

There are two ways to get Cake. If you are a user, the standalone script is easiest. If you want to
help with cake development though, you should check out the `develop` branch of the git
repository. You can always access the stable version of cake, even when running from git, by adding
the `-S` flag to your command.

### Standalone script (stable)

Make sure `~/bin/` is in your `$PATH`, then execute the following command:

    curl http://releases.clojure-cake.org/cake -o ~/bin/cake && chmod +x ~/bin/cake

### Git repository (development)

    git clone git://github.com/flatland/cake.git

Symlink `bin/cake` into your `$PATH`.

Cake will bootstrap itself the first time it starts up.

## Getting Started

Cake is compatible with most Leiningen project.clj files, so if you already have a project.clj,
you're probably ready to go. Just install Cake and then type `cake` in your project root for a list
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

### Get a list of available commands

    cake help

### Get detailed help on a single command

    cake help command-name

### Start an interactive repl with command history and tab completion:

    cake repl

### Run a clojure script

    cake run path/to/script.clj

### Create a new project in the current directory

    cake new project-name

## Documentation

For more detailed documentation, see the [wiki](https://github.com/flatland/cake/wiki).

## Contributors

- Justin Balthrop ([ninjudd](https://github.com/ninjudd))
- Lance Bradley ([lancepantz](https://github.com/lancepantz))
- Anthony Grimes ([Raynes](https://github.com/Raynes))
- Luke Renn ([lrenn](https://github.com/lrenn))
- David Santiago ([davidsantiago](https://github.com/davidsantiago))
- Alan Malloy ([amalloy](https://github.com/amalloy))
- Jeff Rose ([rosejn](https://github.com/rosejn))
- Martin Sander ([marvinthepa](https://github.com/marvinthepa))

## YourKit

YourKit's Java Profiler was a terrific help to us in finding classloader memory leaks when we
switched Cake to use a single JVM with a separate project classloader.

YourKit is kindly supporting open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
[YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and
[YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp).
