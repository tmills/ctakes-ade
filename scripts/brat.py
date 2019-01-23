#!/usr/bin/env python

import re
import sys

class Attribute:
    def __init__(self, cat, id):
        self.cat = cat
        self.id = id

class Entity:
    def __init__(self, id, cat, start, end):
        self.id = id
        self.cat = cat
        self.start = start
        self.end = end

class Relation:
    def __init__(self, id, cat, arg1, arg2):
        self.id = id
        self.cat = cat
        self.arg1 = arg1
        self.arg2 = arg2

brat_ent_patt = re.compile('^(T\d+)\t(.+)\t(.+)$')
brat_rel_patt = re.compile('^(R\d+)\s+(\S+) Arg1:(\S+) Arg2:(\S+)$')

def read_brat_file(ann_fn):
    ents = {}
    rels = {}
    with open(ann_fn, 'r') as ann_file:
        for line in ann_file.readlines():
            line = line.rstrip()
            m = brat_ent_patt.match(line)
            if not m is None:
                ent_id = m.group(1)
                ent_atts = m.group(2).split(' ')
                ent_text = m.group(3)

                if len(ent_atts) == 3:
                    ent_type = ent_atts[0]
                    ent_start_ind = int(ent_atts[1])
                    ent_end_ind = int(ent_atts[2])
                elif len(ent_atts) > 3:
                    ent_type = ent_atts[0]
                    ent_start_ind = int(ent_atts[1])
                    ent_end_ind = int(ent_atts[-1])
                else:
                    sys.stderr.write('File %s had entity line %s that could not be parsed\n' % (ann_fn, line))
                    continue

                ents[ent_id] = Entity(ent_id, ent_type, ent_start_ind, ent_end_ind)
            else:
                m = brat_rel_patt.match(line)
                if not m is None:
                    rel_id = m.group(1)
                    rel_type = m.group(2)
                    a1id = m.group(3)
                    a2id = m.group(4)
                    rels[rel_id] = Relation(rel_id, rel_type, a1id, a2id)
                else:
                    sys.stderr.write('File %s had entity line %s that did not match entities or relations\n' % (ann_fn, line))

    return ents, rels
