(ns cake.tasks.bake
  (:use cake.core))

(deftask eat
  (while (not (.ready *in*))
    (println "the cake is a lie.")
    (Thread/sleep 300)))

(deftask bake
  (println "
                                OMM.
                                 .MM.
                                   OM.
                                .MM
                       . MMMMM.NI .
                      .MMMMMM.MMMMM.                   .. ~DMMMMMMMM.
                  .MM.MMMMMM:MMMMMMM.. . .....~8MMMMMMMD~ ..MMMZ. .IM
                .MMM. MMMMMMMMMMMMMM.NMMMMMD~. ...    .IMMM....OMMMMM
                .MMO. MMMMMMMMMMMMMM   ?MMM      ..MMMD ...DMMMMMMMMM
            8MMMM MM   MMMMMMMMMMMM.   MMM+.   MMMD . .IMMMMMMMMMMMMM
           MM=..  .++  M. OMMMM+.  ..MMO  ~MMM=. ..ZMMMMMMMMMMMMMMMM.
           M8MMM        MM: OMM     . DMMM.....DMMMMMMMMMMMMMMMM ...
           MD  .DMMMN~  ..MM......MMMI.... NMMMMMMMMMMMMMMMM... . ZMM
           MD      . . :$NMMMMMMD.. . .MMMMMMMMMMMMMMMMN  . . DMMMMMM
           MD                   .. MMMMMMMMMMMMMMMM8..  ..MMMMMMMMMMM
           MD                   .MMMMMMMMMMMMMM7.   . MMMMMMMMMMMMMMM
           MD                   .MMMMMMMMMM+.. . .MMMMMMMMMMMMMMMMO..
           MD                   .MMMMMM: . ..,MMMMMMMMMMMMMMMMI.   =M
           MD                   .MM......~MMMMMMMMMMMMMMMM+.   ?MMMMM
           MD                   . ...IMMMMMMMMMMMMMMMM:....OMMMMMMMMM
           MD                    $MMMMMMMMMMMMMMMM ....DMMMMMMMMMMMMM
           MD                   .MMMMMMMMMMMMM+. ..?MMMMMMMMMMMMMMMM
           MD                   .MMMMMMMMM:.  .$MMMMMMMMMMMMMMMM
           MD                   .MMMMM,... DMMMMMMMMMMMMMMMM
           MD                   .M. . .NMMMMMMMMMMMMMMMM
           MD                    . MMMMMMMMMMMMMMMMN
           MM                   .MMMMMMMMMMMMMM8
            MM..                .MMMMMMMMMM7
              NMM:.             .MMMMMM+
                  IMMMMMMMMMMMMMMMM:
"))
