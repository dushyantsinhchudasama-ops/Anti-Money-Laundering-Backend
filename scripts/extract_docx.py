import sys
import zipfile
import xml.etree.ElementTree as ET

def docx_to_text(path):
    ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
    with zipfile.ZipFile(path) as z:
        with z.open('word/document.xml') as f:
            tree = ET.parse(f)
            root = tree.getroot()
            paragraphs = []
            for p in root.findall('.//w:p', ns):
                texts = [t.text for t in p.findall('.//w:t', ns) if t.text]
                if texts:
                    paragraphs.append(''.join(texts))
            return '\n\n'.join(paragraphs)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: extract_docx.py <file.docx>')
        sys.exit(1)
    path = sys.argv[1]
    print(docx_to_text(path))
