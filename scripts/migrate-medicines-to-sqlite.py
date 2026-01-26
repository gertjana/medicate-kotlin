#!/usr/bin/env python3
"""
Migration script to convert medicines.json to SQLite database.
This is a one-time migration to improve memory efficiency.
"""

import json
import sqlite3
import sys
import os
from pathlib import Path

def create_database(db_path):
    """Create the medicines SQLite database with appropriate schema."""
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # Create the medicines table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS medicines (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            registratienummer TEXT,
            soort TEXT,
            productnaam TEXT NOT NULL,
            inschrijvingsdatum TEXT,
            handelsvergunninghouder TEXT,
            afleverstatus TEXT,
            farmaceutischevorm TEXT,
            potentie TEXT,
            procedurenummer TEXT,
            toedieningsweg TEXT,
            aanvullendemonitoring TEXT,
            smpc_filenaam TEXT,
            bijsluiter_filenaam TEXT,
            par_filenaam TEXT,
            spar_filenaam TEXT,
            armm_filenaam TEXT,
            smpc_wijzig_datum TEXT,
            bijsluiter_wijzig_datum TEXT,
            atc TEXT,
            werkzamestoffen TEXT,
            hulpstoffen TEXT,
            productdetail_link TEXT,
            nieuws_links TEXT,
            nieuws_link_datums TEXT,
            referentie TEXT,
            smpc_vorige_versie TEXT,
            smpc_vorige_vorige_versie TEXT
        )
    """)

    # Create indices for faster searching
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_productnaam ON medicines(productnaam)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_werkzamestoffen ON medicines(werkzamestoffen)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_farmaceutischevorm ON medicines(farmaceutischevorm)")

    conn.commit()
    return conn

def migrate_json_to_sqlite(json_path, db_path):
    """Migrate medicines.json to SQLite database."""
    print(f"Reading medicines from: {json_path}")

    if not os.path.exists(json_path):
        print(f"ERROR: JSON file not found: {json_path}")
        sys.exit(1)

    with open(json_path, 'r', encoding='utf-8') as f:
        medicines = json.load(f)

    print(f"Found {len(medicines)} medicines to migrate")

    # Create database
    print(f"Creating SQLite database: {db_path}")
    conn = create_database(db_path)
    cursor = conn.cursor()

    # Insert medicines in batches
    batch_size = 1000
    total_inserted = 0

    for i in range(0, len(medicines), batch_size):
        batch = medicines[i:i+batch_size]

        for medicine in batch:
            cursor.execute("""
                INSERT INTO medicines (
                    registratienummer, soort, productnaam, inschrijvingsdatum,
                    handelsvergunninghouder, afleverstatus, farmaceutischevorm,
                    potentie, procedurenummer, toedieningsweg, aanvullendemonitoring,
                    smpc_filenaam, bijsluiter_filenaam, par_filenaam, spar_filenaam,
                    armm_filenaam, smpc_wijzig_datum, bijsluiter_wijzig_datum,
                    atc, werkzamestoffen, hulpstoffen, productdetail_link,
                    nieuws_links, nieuws_link_datums, referentie,
                    smpc_vorige_versie, smpc_vorige_vorige_versie
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                medicine.get('registratienummer', ''),
                medicine.get('soort', ''),
                medicine.get('productnaam', ''),
                medicine.get('inschrijvingsdatum', ''),
                medicine.get('handelsvergunninghouder', ''),
                medicine.get('afleverstatus', ''),
                medicine.get('farmaceutischevorm', ''),
                medicine.get('potentie', ''),
                medicine.get('procedurenummer', ''),
                medicine.get('toedieningsweg', ''),
                medicine.get('aanvullendemonitoring', ''),
                medicine.get('smpc_filenaam', ''),
                medicine.get('bijsluiter_filenaam', ''),
                medicine.get('par_filenaam', ''),
                medicine.get('spar_filenaam', ''),
                medicine.get('armm_filenaam', ''),
                medicine.get('smpc_wijzig_datum', ''),
                medicine.get('bijsluiter_wijzig_datum', ''),
                medicine.get('atc', ''),
                medicine.get('werkzamestoffen', ''),
                medicine.get('hulpstoffen', ''),
                medicine.get('productdetail_link', ''),
                medicine.get('nieuws_links', ''),
                medicine.get('nieuws_link_datums', ''),
                medicine.get('referentie', ''),
                medicine.get('smpc_vorige_versie', ''),
                medicine.get('smpc_vorige_vorige_versie', '')
            ))

        conn.commit()
        total_inserted += len(batch)
        print(f"Inserted {total_inserted}/{len(medicines)} medicines...")

    conn.close()
    print(f"\n✓ Migration complete!")
    print(f"  Total medicines: {total_inserted}")
    print(f"  Database: {db_path}")

    # Get database file size
    db_size_mb = os.path.getsize(db_path) / (1024 * 1024)
    json_size_mb = os.path.getsize(json_path) / (1024 * 1024)
    print(f"  Database size: {db_size_mb:.2f} MB")
    print(f"  Original JSON size: {json_size_mb:.2f} MB")
    print(f"  Space saved in memory: ~{json_size_mb:.2f} MB (JSON is no longer loaded into RAM)")

if __name__ == "__main__":
    # Get data directory from environment or use default
    data_dir = os.environ.get('MEDICINES_DATA_DIR', 'data')

    json_path = os.path.join(data_dir, 'medicines.json')
    db_path = os.path.join(data_dir, 'medicines.db')

    print("=" * 60)
    print("Medicine Database Migration")
    print("=" * 60)
    print(f"Data directory: {data_dir}")
    print()

    migrate_json_to_sqlite(json_path, db_path)
