"""Pack the DB-IP IP to Country Lite CSV into the lookup format used by Orion."""

import csv
import gzip
import ipaddress
import sys
from pathlib import Path


def build(source, destination):
    ranges = {4: [], 6: []}
    with gzip.open(source, 'rt', encoding='utf-8', newline='') as file:
        for start, end, country in csv.reader(file):
            first, last = ipaddress.ip_address(start), ipaddress.ip_address(end)
            if len(country) == 2 and country.isascii():
                ranges[first.version].append((int(first), int(last), country.encode('ascii')))
    with Path(destination).open('wb') as output:
        output.write(len(ranges[4]).to_bytes(4, 'big'))
        for version in (4, 6):
            for first, last, country in sorted(ranges[version]):
                output.write(first.to_bytes(16, 'big') + last.to_bytes(16, 'big') + country)


if __name__ == '__main__':
    build(*sys.argv[1:])
