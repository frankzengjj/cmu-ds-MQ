DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" > /dev/null && pwd )"
source $DIR/common.sh

java ${LOGGER_OPTS} -jar $ARTIFACT "$@"