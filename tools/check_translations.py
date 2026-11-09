"""Check resource parity, format arguments and references without Android SDK."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'

def resources(folder):
    result = {}
    for path in (res / folder).glob('*.xml'):
        for item in ET.parse(path).getroot():
            if item.tag not in ('string', 'plurals'):
                continue
            key = (item.tag, item.attrib['name'])
            assert key not in result, ('Duplicate resource', key)
            result[key] = item
    return result

fr = resources('values')
locales = ['values-en', 'values-es', 'values-de', 'values-it', 'values-pt-rBR', 'values-nl',
           'values-pl', 'values-tr', 'values-ar', 'values-ja', 'values-ko', 'values-b+zh+Hans']
# Plural categories differ by language; do not force the French categories.
# Reference: https://www.unicode.org/cldr/charts/48/supplemental/language_plural_rules.html
required_quantities = {
    'values-pl': {'one', 'few', 'many', 'other'},
    'values-tr': {'one', 'other'},
    'values-ar': {'zero', 'one', 'two', 'few', 'many', 'other'},
    'values-ja': {'other'}, 'values-ko': {'other'}, 'values-b+zh+Hans': {'other'},
}
args = lambda s: sorted(re.findall(r'%\d+\$[dsf]', s))
for locale in locales:
    translated = resources(locale)
    assert fr.keys() == translated.keys(), (locale, 'Different resource keys', fr.keys() ^ translated.keys())
    for key in fr:
        a, b = fr[key], translated[key]
        if key[0] == 'plurals':
            af = {x.attrib['quantity']: x.text or '' for x in a}
            bf = {x.attrib['quantity']: x.text or '' for x in b}
            expected_quantities = required_quantities.get(locale, set(af))
            assert set(bf) == expected_quantities, (locale, key, set(bf), expected_quantities)
            for q in bf:
                assert bf[q].strip('" ').strip(), (locale, key, 'Empty text')
                expected_args = args(af.get(q, af['other']))
                # Arabic spells out zero/one/two; these forms intentionally omit the count.
                if locale == 'values-ar' and q in {'zero', 'one', 'two'}:
                    assert args(bf[q]) in ([], expected_args), (locale, key, q)
                else:
                    assert expected_args == args(bf[q]), (locale, key, q)
        else:
            assert (b.text or '').strip('" ').strip(), (locale, key, 'Empty text')
            assert args(a.text or '') == args(b.text or ''), (locale, key)
    print(f'OK: {locale}: {len(translated)} resources, matching format arguments.')

for path in (root / 'app/src/main/java').rglob('*.kt'):
    for kind, name in re.findall(r'(?<!android\.)\bR\.(string|plurals)\.(\w+)', path.read_text()):
        assert (kind, name) in fr, ('Missing resource', path, kind, name)

print(f'OK: {len(fr)} resources in {len(locales) + 1} languages; Kotlin references match.')
