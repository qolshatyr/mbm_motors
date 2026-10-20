#!/usr/bin/env python3
"""Fill only the «Категория» column of a CloudShop product export with the classified category.

    python3 tools/catalog/fill_categories.py docs/catalog-<id>.xlsx docs/catalog/catalog-<id>-categories.xlsx

The workbook is edited at the XML level: only the cells of the «Категория» column change (they
point to new shared strings), every other part of the file is copied byte for byte, so the result
can be imported back into CloudShop. The column holds CloudShop tags separated by «, »: section,
subsection, car makes with their models and «Бренд …» for each part brand. Products where nothing
was recognised keep their empty value. A trial file with the header and the first five filled products is
written next to the result.
"""
import re
import sys
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape, unescape

if __package__:
    from .classify_products import classify
else:  # run as a script
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from classify_products import classify

COLUMN = 'Категория'
SHEET = 'xl/worksheets/sheet.xml'
STRINGS = 'xl/sharedStrings.xml'
CELL = re.compile(r'<x:c r="([A-Z]+)(\d+)"([^>]*)><x:v>(\d+)</x:v></x:c>')
ROW = re.compile(r'<x:row r="(\d+)"[^>]*>.*?</x:row>', re.S)


def tags(name):
    """«Ходовая часть, Амортизаторы и опоры, Hyundai, Sonata, Kia, Optima, Бренд HPT»."""
    result = classify(name)
    values = [result['category'], result['subcategory']] if result['group'] else []
    for make in result['makes']:
        values.append(make)
        values.extend(f['model'] for f in result['fitments'] if f['make'] == make and f['model'])
    values.extend(f'Бренд {brand}' for brand in result['brands'])
    # A comma would split one tag into two on import.
    values = [v.replace(',', ' ').strip() for v in values if v]
    return ', '.join(dict.fromkeys(values)) or None


category = tags


def shared_strings(xml):
    items = re.findall(r'<x:si>(.*?)</x:si>', xml, re.S)
    return [unescape(''.join(re.findall(r'<x:t[^>]*>(.*?)</x:t>', item, re.S))) for item in items]


def column_letter(sheet_xml, strings, title):
    for letter, row, _attrs, index in CELL.findall(sheet_xml.split('</x:row>', 1)[0]):
        if row == '1' and strings[int(index)] == title:
            return letter
    raise SystemExit(f'Колонка «{title}» не найдена')


def fill(sheet_xml, strings_xml):
    strings = shared_strings(strings_xml)
    name_col, target_col = column_letter(sheet_xml, strings, 'Наименование'), column_letter(sheet_xml, strings, COLUMN)
    names = {row: strings[int(index)] for letter, row, _a, index in CELL.findall(sheet_xml) if letter == name_col}
    values = {row: category(name) for row, name in names.items() if row != '1'}
    new_strings = sorted({v for v in values.values() if v})
    first = len(strings)
    index_of = {value: first + i for i, value in enumerate(new_strings)}

    def replace(match):
        letter, row, attrs, index = match.groups()
        value = values.get(row) if letter == target_col else None
        return f'<x:c r="{letter}{row}"{attrs}><x:v>{index_of[value]}</x:v></x:c>' if value else match.group(0)

    sheet_xml = CELL.sub(replace, sheet_xml)
    added = ''.join(f'<x:si><x:t>{escape(value)}</x:t></x:si>' for value in new_strings)
    strings_xml = strings_xml.replace('</x:sst>', added + '</x:sst>')
    for attribute, increase in [('count', sum(1 for v in values.values() if v)), ('uniqueCount', len(new_strings))]:
        strings_xml = re.sub(rf'{attribute}="(\d+)"', lambda m: f'{attribute}="{int(m.group(1)) + increase}"',
                             strings_xml, count=1)
    filled = [row for row, v in values.items() if v]
    return sheet_xml, strings_xml, len(filled), len(values) - len(filled), filled


def write(source, target, replacements):
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, 'w') as result:
        for info in original.infolist():
            data = replacements.get(info.filename)
            result.writestr(info, data.encode('utf-8') if data is not None else original.read(info.filename))


def trial(sheet_xml, rows):
    keep = {'1', *rows[:5]}
    sheet_xml = ROW.sub(lambda m: m.group(0) if m.group(1) in keep else '', sheet_xml)
    return re.sub(r'<x:dimension ref="[^"]*"', f'<x:dimension ref="A1:AK{len(keep)}"', sheet_xml, count=1)


def main(source, target):
    with zipfile.ZipFile(source) as book:
        sheet_xml = book.read(SHEET).decode('utf-8')
        strings_xml = book.read(STRINGS).decode('utf-8')
    sheet_xml, strings_xml, filled, empty, rows = fill(sheet_xml, strings_xml)
    write(source, target, {SHEET: sheet_xml, STRINGS: strings_xml})
    trial_path = Path(target).with_name(Path(target).stem + '-test5.xlsx')
    write(source, trial_path, {SHEET: trial(sheet_xml, rows), STRINGS: strings_xml})
    print(f'Категория заполнена: {filled}; без категории: {empty}')
    print('Результат:', target)
    print('Пробный файл (5 товаров):', trial_path)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
