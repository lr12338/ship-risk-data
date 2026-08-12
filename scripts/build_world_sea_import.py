#!/usr/bin/env python3
"""Convert the fixed SQLyog world_sea dump into a schema-scoped, auditable import file."""

import argparse
import hashlib
import re
import struct
from pathlib import Path

PREFIX = b"insert  into `world_sea`(`ID`,`NAME`,`_ID`,`Gazetteer_`,`the_geom`,`CNNAME`) values ("
STAGE_RE = re.compile(r"^ship_behavior_world_sea_stage_\d{8}_\d{6}$")


def split_values(raw):
    values, current, quoted, escaped = [], bytearray(), False, False
    for byte in raw:
        if escaped:
            current.extend((92, byte))
            escaped = False
        elif quoted and byte == 92:
            escaped = True
        elif byte == 39:
            quoted = not quoted
            current.append(byte)
        elif byte == 44 and not quoted:
            values.append(bytes(current).strip())
            current.clear()
        else:
            current.append(byte)
    values.append(bytes(current).strip())
    if quoted or escaped or len(values) != 6:
        raise ValueError("invalid INSERT value list")
    return values


def unescape_mysql(value):
    if not (value.startswith(b"'") and value.endswith(b"'")):
        raise ValueError("expected quoted SQL literal")
    source, result, index = value[1:-1], bytearray(), 0
    escapes = {48: 0, 98: 8, 110: 10, 114: 13, 116: 9, 90: 26,
               39: 39, 34: 34, 92: 92, 37: 37, 95: 95}
    while index < len(source):
        if source[index] == 92:
            index += 1
            if index >= len(source):
                raise ValueError("trailing SQL escape")
            result.append(escapes.get(source[index], source[index]))
        else:
            result.append(source[index])
        index += 1
    return bytes(result)


def text(value):
    if value.upper() == b"NULL":
        return None
    return unescape_mysql(value).decode("utf-8")


def sql_text(value):
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def parse(source):
    rows = []
    with source.open("rb") as handle:
        for line_no, line in enumerate(handle, 1):
            if not line.startswith(PREFIX):
                continue
            if not line.rstrip().endswith(b");"):
                raise ValueError("unterminated INSERT at line %d" % line_no)
            fields = split_values(line[len(PREFIX):line.rfind(b");")])
            internal = unescape_mysql(fields[4])
            if len(internal) < 9:
                raise ValueError("short geometry at line %d" % line_no)
            srid = struct.unpack("<I", internal[:4])[0]
            rows.append((int(fields[0]), text(fields[1]), text(fields[2]),
                         int(fields[3]) if fields[3].upper() != b"NULL" else None,
                         srid, internal[4:], text(fields[5])))
    if len(rows) != 102:
        raise ValueError("expected 102 INSERT rows, got %d" % len(rows))
    if len({row[0] for row in rows}) != len(rows):
        raise ValueError("duplicate ID in source dump")
    return sorted(rows, key=lambda row: row[0])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="world_sea.sql")
    parser.add_argument("--output", required=True)
    parser.add_argument("--stage", required=True)
    args = parser.parse_args()
    if not STAGE_RE.match(args.stage):
        raise SystemExit("stage name is not allowlisted")
    source, output = Path(args.source), Path(args.output)
    rows = parse(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("-- Generated from world_sea.sql; never changes another schema or formal table.\n")
        handle.write("-- source_sha256=%s\n" % hashlib.sha256(source.read_bytes()).hexdigest())
        handle.write("CREATE TABLE `dataanalysis`.`%s` (\n" % args.stage)
        handle.write("  `ID` int(11) NOT NULL, `NAME` varchar(255) DEFAULT NULL,\n")
        handle.write("  `_ID` varchar(255) DEFAULT NULL, `Gazetteer_` int(11) DEFAULT NULL,\n")
        handle.write("  `the_geom` geometry NOT NULL, `CNNAME` varchar(100) DEFAULT NULL,\n")
        handle.write("  PRIMARY KEY (`ID`)\n")
        handle.write(") ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;\n")
        for row in rows:
            identifier, name, source_id, gazetteer, srid, wkb, cnname = row
            handle.write("INSERT INTO `dataanalysis`.`%s` " % args.stage)
            handle.write("(`ID`,`NAME`,`_ID`,`Gazetteer_`,`the_geom`,`CNNAME`) VALUES (")
            handle.write("%d,%s,%s,%s,GeomFromWKB(0x%s,%d),%s);\n" % (
                identifier, sql_text(name), sql_text(source_id),
                "NULL" if gazetteer is None else str(gazetteer), wkb.hex(), srid, sql_text(cnname)))
    print("stage=%s rows=%d max_wkb_bytes=%d output_bytes=%d" % (
        args.stage, len(rows), max(len(row[5]) for row in rows), output.stat().st_size))


if __name__ == "__main__":
    main()
