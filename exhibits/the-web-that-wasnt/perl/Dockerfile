# The web that wasn't: Riverside Public Library as a 1997 site would run it, Apache and CGI scripts in Perl,
# with CGI.pm. forms.html is written by forms.cgi from forms.yaml; library.cgi answers with data only.
#   docker build -t web-that-wasnt exhibits/the-web-that-wasnt/perl
#   docker run --rm -d --name web-that-wasnt -p 8150:80 web-that-wasnt
FROM httpd:2.4
RUN apt-get update && apt-get install -y --no-install-recommends libcgi-pm-perl libyaml-libyaml-perl && rm -rf /var/lib/apt/lists/*
# CGI on, and forms.html served by the script that writes it.
RUN sed -i 's/^\s*#LoadModule cgid_module/LoadModule cgid_module/' conf/httpd.conf \
 && echo 'ScriptAlias /forms.html /usr/local/apache2/cgi-bin/forms.cgi' >> conf/httpd.conf \
 && mkdir -p /data && chown www-data:www-data /data
COPY forms.yaml /usr/local/apache2/forms.yaml
COPY cgi-bin/ /usr/local/apache2/cgi-bin/
COPY htdocs/ /usr/local/apache2/htdocs/
# The description itself, for anyone who knows to look for it.
COPY forms.yaml /usr/local/apache2/htdocs/forms.yaml
RUN chmod 755 /usr/local/apache2/cgi-bin/*.cgi
