#!/usr/bin/env python

from os.path import join, isfile
import sys
import glob

import nltk
from nltk import pos_tag, word_tokenize, sent_tokenize
from nltk.tokenize.treebank import TreebankWordTokenizer
from brat import *

def main(args):
    if len(args) < 2:
        sys.stderr.write('Required arguments: <input dir> <output dir>\n')
        sys.exit(-1)

    # get all .txt files from the chqa directory:
    txt_files = glob.glob(join(args[0], '*.txt'))

    fout = open(join(args[1], 'ade-all-entities.conll'), 'w')

    tokenizer = TreebankWordTokenizer()

    for txt_fn in txt_files:
        ann_fn = txt_fn[:-3] + 'ann'
        if not isfile(ann_fn): continue

        print('Processing file %s which has corresponding file %s' % (txt_fn, ann_fn))

        with open(txt_fn, 'r') as myfile:
            text = myfile.read()

        ents,_ = read_brat_file(ann_fn)
        sents = sent_tokenize(text)

        awaited_starts = {}
        for ent_id in ents.keys():
            ent = ents[ent_id]
            name = ent.cat
            awaited_starts[ent.start] = ent_id

        start_ind = 0
        prev_label = 'O'
        awaited_end = None # list of (end_ind, label) pairs
        for sent in sents:
            tagged = pos_tag(tokenizer.tokenize(sent))
            for tag in tagged:
                # tag[0] is the word and tag[1] is the POS
                token_start_ind = text.find(tag[0], start_ind)
                token_end_ind = token_start_ind + len(tag[0])
                start_ind = token_end_ind

                ## For BIO tagging, active label gets printed as I
                if not awaited_end is None:
                    fout.write('%s\t%s\tI-%s\n' % (tag[0], tag[1], awaited_end[1]))

                    ## Remove entity if it ends on this token:
                    if awaited_end[0] <= token_end_ind:
                        awaited_end = None

                ## All awaited starts get printed as B
                elif token_start_ind in awaited_starts:
                    if not awaited_end is None:
                        raise Exception('There is overlap between an existing entity and this one!')
                    ent_id = awaited_starts[token_start_ind]
                    ent = ents[ent_id]
                    fout.write('%s\t%s\tB-%s\n' % (tag[0], tag[1], ent.cat))

                    awaited_starts.pop(token_start_ind)
                    if ent.end > token_end_ind:
                        awaited_end = (ent.end, ent.cat)

                else:
                    fout.write('%s\t%s\tO\n' % (tag[0], tag[1]))

                fout.flush()

            # break
            fout.write('\n')
    fout.close()

if __name__ == '__main__':
    main(sys.argv[1:])
