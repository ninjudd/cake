(ns cake.tasks.bake
  (:use cake.core
        [cake.utils.compress :only [unsmash]]))

(deftask eat
  (while (not (.ready *in*))
    (println "the cake is a lie.")
    (Thread/sleep 300)))

(deftask bake
  (println (unsmash "eJydlLGOwyAMhvc8hYduVF4bIVVZWBgMex6GZz9sSO7ApKHnSiUhvz9sY1jgxiIRLnciwCkV0+5l\nmXWlQSA2DB6uOEhFUv5HAoTkqNqIgoc32UOFeWVkS2udcilP5HHPH7yOF2VxagxD9RSWqLzAEaN8\nGVFiT8nTWxkkFwYy7syopUi4IPImlPJu6ugASyw0pBC9OV40Jj8jSLDsyqtHgEQsyG87ddZUl9Yz\nbEZa5tRNdbUMf7I4ss2TDcRlPatCKuuLG1fB8whBhSB7t3cJuVq+/LOP6uPKLveAUGRuUBane4e7\nqyesWGs1Lu6Q0hJeCGfv/49ixslFXd0ripUj8OwJnkN7T8VStir1BGnCbTIj7gffE+zvEZqiwKMn\ngG6+L/fIcGhbz/0CYrlJVGCTgCe3uTo8E87SFerQ3FdQd3lob4yPuYqtzUUll+y1w6u7G0MpmBab\nwYWuG2b5Aaa0YDM=")))
