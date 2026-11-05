#!/bin/sh
# Runs library.cgi as Apache would, with the CGI environment and a POST body on standard input, and keeps each
# answer in captures/: the sign-in form, her loans page with its forms, Matilda renewed from its own button, and a
# renewal of The Hobbit sent anyway (no page offered it), refused.
#   sh married/run.sh
set -eu
here=$(cd "$(dirname "$0")" && pwd)
data=$(mktemp -d)
mkdir -p "$here/captures"

cgi() {  # method, path, body, capture
    printf '%s' "$3" | LIBRARY_DATA="$data/loans.txt" REQUEST_METHOD="$1" PATH_INFO="$2" SCRIPT_NAME=/cgi-bin/library.cgi \
        CONTENT_TYPE=application/x-www-form-urlencoded CONTENT_LENGTH=${#3} GATEWAY_INTERFACE=CGI/1.1 \
        perl "$here/library.cgi" > "$here/captures/$4"
    echo "$1 $2: $(sed -n 1p "$here/captures/$4" | tr -d '\r')  -> captures/$4"
}

signed='card=21000451&pin=1234'
cgi POST /_reset '' reset.html
cgi GET /loans '' sign-in.html
cgi POST /loans "$signed" loans.html
cgi POST /loans/3311/renew "$signed" renewed.html
cgi POST /loans/3310/renew "$signed" refused.html
rm -rf "$data"
# Her loans page again, one tag to a line, for reading on a slide.
perl -pe 's/>\s*</>\n</g' "$here/captures/loans.html" > "$here/captures/loans.lines.html"
