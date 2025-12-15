#
# build the Crystal Palace distribution
#

# don't forget to change the rm -rf at the bottom too
FOLDER=dist

ant clean ; ant

mkdir $FOLDER

cp -r build/* $FOLDER
cp LICENSE README $FOLDER

cd demo
make clean ; make
cd ..
cp -r demo $FOLDER

YYMMDD=`date +%y%m%d`

tar zcvf cpdist20$YYMMDD.tgz $FOLDER

# I am not using an environment variable, because I'm taking NO chances with this command.
rm -rf dist

# generate our JavaDocs
ant docs
tar zcvf api.tgz api
rm -rf api

# do our source code archive too
git archive --format tgz --output cpsrc20$YYMMDD.tgz --prefix cpsrc/ master
