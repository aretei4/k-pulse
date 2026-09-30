import re
import pandas as pd
from pypdf import PdfReader

def extract_voter_data_from_pdf(pdf_path: str, start_page: int, end_page: int, output_excel: str = "extracted_pdf_voters.xlsx"):
    """
    Extracts text from specified PDF pages, parses pipe/tabular structures,
    and exports the result to an Excel sheet.
    """
    reader = PdfReader(pdf_path)
    records = []
    
    # 1. Iterate through specified pages (1-indexed)
    for page_num in range(start_page - 1, min(end_page, len(reader.pages))):
        page = reader.pages[page_num]
        text = page.extract_text()
        
        # 2. Process each line
        lines = text.split("\n")
        for line in lines:
            # Match lines containing voter records (e.g. pipe separated or tabular rows)
            if "|" in line:
                parts = [p.strip() for p in line.split("|")]
                if len(parts) >= 8:
                    # Normalize row length to 9 columns
                    if len(parts) < 9:
                        parts.extend([""] * (9 - len(parts)))
                    elif len(parts) > 9:
                        parts = parts[:9]
                    records.append(parts)

    # 3. Create DataFrame
    columns = [
        "Serial_No", "Section_No", "House_No", "Voter_Name",
        "Relationship", "Relative_Name", "Gender", "Age", "EPIC_Card_No"
    ]
    df = pd.DataFrame(records, columns=columns)

    # 4. Save to Excel
    df.to_excel(output_excel, index=False)
    print(f"Extracted {len(df)} records from pages {start_page} to {end_page}.")
    print(f"Saved output to: {output_excel}")
    return df

if __name__ == "__main__":
    # Example usage: extract pages 1 to 10 from your PDF file
    extract_voter_data_from_pdf("P030028.pdf", start_page=1, end_page=10)