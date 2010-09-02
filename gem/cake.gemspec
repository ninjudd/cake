Gem::Specification.new do |s|
  s.name = 'cake'
  s.rubyforge_project = 'cake'
  s.version = ENV['CAKE_VERSION']
  s.authors = ['Justin Balthrop', 'Lance Bradley']
  s.date = Time.now
  s.default_executable = 'cake'
  s.summary = 'A tasty build tool and concurrent repl for Clojure.'
  s.description = "Save your fork, there's cake!"
  s.email = 'code@justinbalthrop.com'
  s.executables = ['cake']
  s.files = ['cake.gemspec', 'bin/cake', 'lib/cake.jar', 'lib/bake.jar', 'lib/clojure.jar']
  s.homepage = 'http://github.com/ninjudd/cake'
end
