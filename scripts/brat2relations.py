#!/usr/bin/env python

from os.path import join, isfile
import sys
import glob

import nltk
from nltk import pos_tag, word_tokenize, sent_tokenize
from nltk.tokenize.treebank import TreebankWordTokenizer
from nltk.tokenize.punkt import PunktSentenceTokenizer
from brat import read_brat_file

def get_span_ents(span, ents):
    drug_ents = []
    att_ents = []
    for ent in ents.values():
        if ent.start > span[0] and ent.end < span[1]:
            if ent.cat == 'Drug':
                drug_ents.append(ent)
            else:
                att_ents.append(ent)
    
    return drug_ents, att_ents

def get_label(rels, ents, arg1_ent, arg2_ent):
    for rel in rels.values():
        if ents[rel.arg1] == arg1_ent and ents[rel.arg2] == arg2_ent:
            return rel.cat
    
    return "None"


def main(args):
    if len(args) < 2:
        sys.stderr.write('Required arguments: <input dir> <output dir>\n')
        sys.exit(-1)

    sent_tokenizer = PunktSentenceTokenizer()

    # get all .txt files from the chqa directory:
    txt_files = glob.glob(join(args[0], '*.txt'))
    rel_out = open(join(args[1], 'ade-all-relations.flair'), 'w')

    for txt_fn in txt_files:
        ann_fn = txt_fn[:-3] + 'ann'
        if not isfile(ann_fn): continue

        print('Processing file %s which has corresponding file %s' % (txt_fn, ann_fn))

        with open(txt_fn, 'r') as myfile:
            text = myfile.read()
        ents,rels = read_brat_file(ann_fn)

        sent_spans = sent_tokenizer.span_tokenize(text)

        for sent_span in sent_spans:
            sent = text[sent_span[0]:sent_span[1]].replace('\n', ' ')
            drug_ents, att_ents = get_span_ents(sent_span, ents)

            for att_ent in att_ents:
                for drug_ent in drug_ents:
                    label = get_label(rels, ents, att_ent, drug_ent)

                    ## Get index of ents into sent:
                    a1_start = att_ent.start - sent_span[0]
                    a1_end = att_ent.end - sent_span[0]

                    a2_start = drug_ent.start - sent_span[0]
                    a2_end = drug_ent.end - sent_span[0]

                    if a1_start < a2_start:
                        # arg1 occurs before arg2
                        rel_text = (sent[:a1_start] + 
                                    "<%s> %s </%s>" % (att_ent.cat, sent[a1_start:a1_end], att_ent.cat) +
                                    sent[a1_end:a2_start] +
                                    "<drug> %s </drug>" % (sent[a2_start:a2_end]) +
                                    sent[a2_end:])
                    else:
                        rel_text = (sent[:a2_start] +
                                    "<drug> %s </drug>" % (sent[a2_start:a2_end]) +
                                    sent[a2_end:a1_start] +
                                    "<%s> %s </%s>" % (att_ent.cat, sent[a1_start:a1_end], att_ent.cat) +
                                    sent[a1_end:])
                    ## lookup flair classification format
                    rel_out.write('__label__%s %s \n' % (label, rel_text))
    
    rel_out.close()
if __name__ == '__main__':
    main(sys.argv[1:])




