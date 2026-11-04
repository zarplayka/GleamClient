"""Consistent daily SQLite backup; private files, atomic replacement, 30-day retention."""
import datetime
import os
import sqlite3
import time
from pathlib import Path

source = Path("/var/lib/orion-cosmetics/state.db")
directory = Path("/var/backups/orion-cosmetics")
directory.mkdir(mode=0o700, parents=True, exist_ok=True)
target = directory / (datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d") + ".sqlite")
temporary = target.with_suffix(".sqlite.tmp")
with sqlite3.connect("file:" + str(source) + "?mode=ro", uri=True) as live:
    with sqlite3.connect(temporary) as snapshot:
        live.backup(snapshot)
os.chmod(temporary, 0o600)
os.replace(temporary, target)
for old in directory.glob("*.sqlite"):
    if old.parent.resolve() == directory.resolve() and old.stat().st_mtime < time.time() - 30 * 86400:
        old.unlink()
print("Orion cosmetics database backup completed")
