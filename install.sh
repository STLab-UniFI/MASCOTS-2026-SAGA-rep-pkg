cd calculus/

mvn install:install-file \
   -Dfile=libs/eulero-1.0.0-SNAPSHOT.jar \
   -DgroupId=org.oris-tool \
   -DartifactId=eulero \
   -Dversion=1.0.0-SNAPSHOT \
   -Dpackaging=jar

mvn install:install-file \
   -Dfile=libs/sirio-2.0.5.jar \
   -DgroupId=org.oris-tool \
   -DartifactId=sirio \
   -Dversion=2.0.5 \
   -Dpackaging=jar

mvn clean package

cd ..