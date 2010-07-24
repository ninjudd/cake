Gem::Specification.new do |s|
  s.name = 'bake.clj'
  s.rubyforge_project = 'bake.clj'
  s.version = ENV['BAKE_VERSION']
  s.authors = ['Justin Balthrop', 'Lance Bradley']
  s.date = Time.now
  s.default_executable = 'bake'
  s.summary = 'Build tools got you down? Get baked!'
  s.description = 'Add equal parts clojure, ruby, love, and tenderness. Bake at 350 for 1 minute. Delicious.'
  s.email = 'code@justinbalthrop.com'
  s.executables = ['bake']
  s.files = ['bake.clj.gemspec', 'bin/bake', 'lib/bake.jar', 'lib/cake.jar']
  s.homepage = 'http://github.com/ninjudd/bake'
end
