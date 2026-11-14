#!/usr/bin/perl
# library.cgi -- Riverside Public Library's loans, in the web that wasn't. Every answer is data only: numbers to
# copy, no links, nothing to press. What you can do is in forms.html, not here; you find out whether you could
# only after you send it. GET reads the query string, POST the URL-encoded body, both through CGI.pm's param().
use strict;
use warnings;
use CGI qw(:standard -no_xhtml);
use Fcntl qw(:flock);
use Time::Local qw(timegm);

my $DATA = $ENV{LIBRARY_DATA} || '/data/loans.txt';
my %MEMBER = (card => '21000451', pin => '1234');    # Ana Torres
my @SEED = (
    # loan  book  title              author              due          renewals hold returned
    [3310, 4471, 'The Hobbit',      'J. R. R. Tolkien', '1997-11-12', 0, 1, 0],
    [3311, 4472, 'Matilda',         'Roald Dahl',       '1997-11-12', 0, 0, 0],
    [3312, 4473, "Charlotte's Web", 'E. B. White',      '1997-11-12', 3, 0, 0],
);
my $MOST_RENEWALS = 3;

my $q = CGI->new;
my $path = $q->path_info || '/';
my $method = $q->request_method || 'GET';

open(my $lock, '>>', "$DATA.lock") or die "cannot lock: $!";
flock($lock, LOCK_EX);
my @loans = load();

if ($method eq 'POST' && $path eq '/_reset') { @loans = map { [@$_] } @SEED; save(); answer('200 OK', 'Reset', p('The seed is back.')); }
elsif (($q->param('card') // '') ne $MEMBER{card} || ($q->param('pin') // '') ne $MEMBER{pin}) {
    answer('401 Unauthorized', 'Not signed in', p('Wrong card number or PIN.'));
}
elsif ($method eq 'GET' && $path eq '/loans') { list(); }
elsif ($method eq 'GET' && $path =~ m{^/loans/(\d+)$}) { one($1); }
elsif ($method eq 'POST' && $path =~ m{^/loans/(\d+)/(renew|return)$}) { act($1, $2); }
else { answer('404 Not Found', 'Not found', p("Nothing is at $path. See forms.html.")); }
close $lock;
exit;

sub list {
    my @rows = (Tr(th(['Loan number', 'Book number', 'Title', 'Due'])));
    push @rows, Tr(td([$_->[0], $_->[1], escapeHTML($_->[2]), $_->[7] ? 'returned' : $_->[4]])) for @loans;
    answer('200 OK', 'Your loans', table({ -border => 1, -cellpadding => 4 }, @rows));
}

sub one {
    my ($id) = @_;
    my $loan = find($id) or return answer('404 Not Found', 'No such loan', p("There is no loan $id."));
    my @rows = map { Tr(td(b($_->[0])), td(escapeHTML($_->[1]))) }
        (['Loan number', $loan->[0]], ['Book number', $loan->[1]], ['Title', $loan->[2]], ['Author', $loan->[3]],
         ['Due', $loan->[4]], ['Renewals', $loan->[5]], ['Returned', $loan->[7] ? 'yes' : 'no']);
    answer('200 OK', "Loan $id", table({ -border => 1, -cellpadding => 4 }, @rows));
}

sub act {
    my ($id, $what) = @_;
    my $loan = find($id) or return answer('404 Not Found', 'No such loan', p("There is no loan $id."));
    return answer('409 Conflict', 'Cannot do that', p('It was returned already.')) if $loan->[7];
    if ($what eq 'return') {
        $loan->[7] = 1;
        save();
        return answer('200 OK', 'Returned', p("Loan $id is returned. Thank you."));
    }
    return answer('409 Conflict', 'Cannot renew', p("A hold is waiting for this book, so it can't be renewed."))
        if $loan->[6];
    return answer('409 Conflict', 'Cannot renew', p("It has been renewed $MOST_RENEWALS times, the most allowed."))
        if $loan->[5] >= $MOST_RENEWALS;
    my ($y, $m, $d) = split /-/, $loan->[4];
    my @due = gmtime(timegm(0, 0, 12, $d, $m - 1, $y) + 21 * 86400);
    $loan->[4] = sprintf('%04d-%02d-%02d', $due[5] + 1900, $due[4] + 1, $due[3]);
    $loan->[5]++;
    save();
    answer('200 OK', 'Renewed', p("Loan $id is renewed. It is due $loan->[4]."));
}

sub find { my ($id) = @_; my ($loan) = grep { $_->[0] == $id } @loans; return $loan; }

sub answer {
    my ($status, $title, @body) = @_;
    print header(-status => $status, -type => 'text/html', -charset => 'ISO-8859-1');
    print start_html(-title => "Riverside Public Library -- $title", -bgcolor => '#FFFFFF'),
          h2($title), @body, hr(), p(small("Riverside Public Library. $status.")), end_html();
    return;
}

sub load {
    return map { [@$_] } @SEED unless -e $DATA;
    open(my $in, '<', $DATA) or die "cannot read $DATA: $!";
    my @read = map { chomp; [split /\t/] } <$in>;
    close $in;
    return @read;
}

sub save {
    open(my $out, '>', $DATA) or die "cannot write $DATA: $!";
    print {$out} join("\t", @$_), "\n" for @loans;
    close $out;
}
