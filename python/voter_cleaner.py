import pandas as pd
import numpy as np

def clean_and_standardize_voter_data(file_path: str, output_excel: str = "cleaned_voter_data.xlsx") -> pd.DataFrame:
    """
    Reads, cleans, and standardizes voter list data from a CSV or Excel file.
    """
    # 1. Load File
    if file_path.endswith('.csv'):
        df = pd.read_csv(file_path, dtype=str)
    else:
        df = pd.read_excel(file_path, dtype=str)

    # 2. Trim Whitespace from all string columns
    df = df.applymap(lambda x: x.strip() if isinstance(x, str) else x)

    # Standardize Column Names (Fallback mapping if needed)
    column_rename = {
        df.columns[0]: "Serial_No",
        df.columns[1]: "Section_No",
        df.columns[2]: "House_No",
        df.columns[3]: "Voter_Name",
        df.columns[4]: "Relationship",
        df.columns[5]: "Relative_Name",
        df.columns[6]: "Gender",
        df.columns[7]: "Age",
        df.columns[8]: "EPIC_Card_No"
    }
    df.rename(columns=column_rename, inplace=True)

    # 3. Clean & Standardize Age
    # Extracts digits, removes stray trailing punctuation like '25.' -> 25
    df['Age'] = df['Age'].str.extract(r'(\d+)')[0]
    df['Age'] = pd.to_numeric(df['Age'], errors='coerce')

    # 4. Standardize Gender Code
    gender_map = {
        'M': 'Male',
        'F': 'Female',
        'MALE': 'Male',
        'FEMALE': 'Female'
    }
    df['Gender'] = df['Gender'].str.upper().map(gender_map).fillna(df['Gender'])

    # 5. Standardize Relationship Code
    rel_map = {
        'F': 'Father',
        'H': 'Husband',
        'M': 'Mother'
    }
    df['Relationship_Type'] = df['Relationship'].str.upper().map(rel_map).fillna('Other')

    # 6. Fill Missing Values
    df['EPIC_Card_No'] = df['EPIC_Card_No'].fillna('NOT_AVAILABLE').replace('', 'NOT_AVAILABLE')

    # 7. Deduplicate Data
    initial_count = len(df)
    df.drop_duplicates(subset=['Serial_No', 'Voter_Name', 'House_No'], keep='first', inplace=True)
    duplicates_removed = initial_count - len(df)

    # 8. Export Cleaned Dataset
    df.to_excel(output_excel, index=False)
    print(f"Cleaning complete! {duplicates_removed} duplicates removed. Output saved to {output_excel}")

    return df

# Example Usage:
# cleaned_df = clean_and_standardize_voter_data("extracted_voter_list.csv")