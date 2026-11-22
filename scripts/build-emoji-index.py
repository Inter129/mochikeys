#!/usr/bin/env python3
"""유니코드 CLDR 주석(annotations)에서 이모지 검색 색인을 만든다.
사용: build-emoji-index.py <CLDR xml 폴더> <출력 tsv>
출력 한 줄: 이모지 <TAB> 검색어들(영어·한국어·일본어, '|'로 구분, 소문자)
이모지 판(EmojiData)에 있는 것만 넣고, 판의 순서를 따른다."""
import re, sys, xml.etree.ElementTree as ET
src, out = sys.argv[1], sys.argv[2]
kt = open('app/src/main/kotlin/dev/inter129/mochikeys/view/EmojiPanel.kt', encoding='utf-8').read()
order = []
for m in re.finditer(r'Triple\("[^"]+", "[^"]+", "([^"]+)"\)', kt):
    order += m.group(1).split(' ')
norm = lambda s: s.replace('️', '')
words = {}
for lang in ('en', 'ko', 'ja'):
    for a in ET.parse(f'{src}/{lang}.xml').getroot().iter('annotation'):
        ks = [k.strip().lower() for k in (a.text or '').split('|') if k.strip()]
        words.setdefault(norm(a.get('cp')), []).extend(ks)
n = 0
with open(out, 'w', encoding='utf-8') as f:
    for e in dict.fromkeys(order):
        ks = list(dict.fromkeys(words.get(norm(e), [])))
        if ks: f.write(e + '\t' + '|'.join(ks) + '\n'); n += 1
print(f'{n}/{len(set(order))} emoji indexed')
