#!/usr/bin/env python3
"""
Convert metadata.csv to JSON format.
Uses | as delimiter and first row as field names.
"""

import csv
import json
from pathlib import Path


def convert_csv_to_json(csv_file: str, json_file: str):
    """
    Convert CSV file with | delimiter to JSON.

    Args:
        csv_file: Path to input CSV file
        json_file: Path to output JSON file
    """
    records = []

    with open(csv_file, 'r', encoding='utf-8') as f:
        # Use | as delimiter
        reader = csv.DictReader(f, delimiter='|')

        for row in reader:
            # Clean up any leading/trailing whitespace from values and convert keys to lowercase
            cleaned_row = {k.lower(): v.strip() if v else v for k, v in row.items()}
            records.append(cleaned_row)

    # Write to JSON file with pretty formatting
    with open(json_file, 'w', encoding='utf-8') as f:
        json.dump(records, f, indent=2, ensure_ascii=False)

    print(f"Converted {len(records)} records from {csv_file} to {json_file}")
    return len(records)


def main():
    script_dir = Path(__file__).parent
    csv_file = script_dir / 'metadata.csv'
    json_file = script_dir / 'medicines.json'

    if not csv_file.exists():
        print(f"Error: {csv_file} not found")
        return 1

    count = convert_csv_to_json(str(csv_file), str(json_file))

    print(f"\nSuccess! Created {json_file}")
    print(f"Total records: {count}")

    # Show first record as sample
    with open(json_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
        if data:
            print("\nSample (first record):")
            print(json.dumps(data[0], indent=2, ensure_ascii=False))

    return 0


if __name__ == '__main__':
    exit(main())
