import re
t=open('build.js').read(); nb=open('newblocks.js').read()
new={m.group(1):m.group(2) for m in re.finditer(r'//@(\w+)\n(.*?)//@END',nb,re.S)}
new['MOCK']=open('mockslides.js').read()
head,rest=t.split('// 1. Title',1)
head=head.replace("const arrow = (s, x, y) =>", new['HELPERS']+"const arrow = (s, x, y) =>")
body,tail=('// 1. Title'+rest).split('(async () => {',1)
blocks=re.findall(r'(// (\d+)\. [^\n]*\n\{.*?\n\})\n', body, re.S)
B={int(n):b for b,n in blocks}
S=eval(open('sources.py').read())
def withsrc(n):
    b=B[n]
    if n in S: b=b[:-1]+'  '+S[n]+'\n}'
    return b
order=[1,2,3,4,5,6,7,8,9,'MOCK',10,11,'PROD',13,'KEPT',14,'GH','GL','MAP','CONN',15,'SKILL','WIRING','GHERKIN','PRECOMMIT',16,17,18,'FOUND',20]
out=head+''.join((withsrc(k) if isinstance(k,int) else new[k].strip())+'\n\n' for k in order)+'(async () => {'+tail
open('build2.js','w').write(out); print(len(order),'slides assembled')
