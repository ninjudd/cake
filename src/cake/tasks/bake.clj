(ns cake.tasks.bake
  (:use cake.core
        [cake.utils.compress :only [unsmash]]))

(deftask eat
  (while (not (.ready *in*))
    (println "the cake is a lie.")
    (Thread/sleep 300)))

(deftask bake
  (println (unsmash "eJyVlL2ugzAMhXeewgNSB5BXIiSUJQuDk52H4dmv7fTy40CgVtWGxP56fOK2gdtAIsLm/hwwEdXO\nAYh8NWHlr6gCAPlVEzERMcRdl44L/cd4z9Dyjnvd69K3KnTIEXnBh+Ntt+yUw4xKapvrUaXLGYLW\no5xqWAgXRIAPl27P2rXYrxBRwwv0tMcBwvRJ5fGb25roF4IvopUPDiATJwjNnIFZZ14JC9usJOmO\nBQxHQsit45aZCVFN4YWz1dmhmc6QbBouh8SgzNXWexWIZS/h8pIK9VGnC9K+8wQxCJfnM967+gDJ\n99tZYetp5CuQWQeluFcnBvZvlKAq8BYgl4/hpbEcbQmAk6wfjZXGEAez+QtEHqvzXgGs4knR0ovi\nXlQXY/5iMkdb408/zWfD4vFP5argkN/Y/QnxIrX4r/sM5kKG5g8YvTMj")))
