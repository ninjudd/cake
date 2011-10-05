(ns cake.tasks.slamhound
  (:use cake cake.core slam.hound))

(deftask slamhound
  "Rips your ns form apart and reconstructs it."
  "Pass a file and slamhound will read your ns form and your code and
  try to determine what the ns form should look like."
  {[filename] :slamhound}
  (println (reconstruct filename)))
