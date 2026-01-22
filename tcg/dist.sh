# do our source code archive
YYMMDD=`date +%y%m%d`
git archive --format tgz --output tcg20$YYMMDD.tgz --prefix tcg/ master
