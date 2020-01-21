import anafora
from anafora.select import Select

import os
import sys
from os.path import join
import shutil
from typing import List

import csv
from nltk.tokenize.punkt import PunktSentenceTokenizer

def insert_delimiter_for_entity(text, sent_span, token_span):
    inst_text = text[sent_span[0]:sent_span[1]]
    relative_span = ( token_span[0] - sent_span[0], token_span[1] - sent_span[0])
    inst_text = inst_text[:relative_span[1]] + ' </e> ' + inst_text[relative_span[1]:]
    inst_text = inst_text[:relative_span[0]] + ' <e> ' + inst_text[relative_span[0]:]
    return inst_text

def find_sentence_for_drug(sents: List, token_span: (int,int)) -> str:
    ''' Use binary search to find the sentence corresponding to this span'''
    if len(sents) == 1:
        return sents[0]
    
    halfway = int(len(sents) / 2)
    target = sents[halfway]

    if token_span[0] >= target[0] and token_span[1] <= target[1]:
        return target
    elif token_span[0] < target[0]:
        return find_sentence_for_drug(sents[:halfway], token_span)
    elif token_span[1] > target[1]:
        return find_sentence_for_drug(sents[halfway:], token_span)
    else:
        raise Exception("I'm not sure what happened here -- reached a part of the binary search that I didn't predict, possibly an entity that spans a sentence boundary.")


def main(args):

    if len(args) < 3:
        sys.stderr.write('3 required arguments: <input anafora dir> <output brat dir> <tsv out dir>\n')
        sys.exit(-1)

    sent_tokenizer = PunktSentenceTokenizer()
    neg_out = open( join(args[2], 'negation.tsv'), 'wt')
    dtr_out = open( join(args[2], 'dtr.tsv'), 'wt')
    alink_out = open( join(args[2], 'alink.tsv'), 'wt')

    for sub_dir, text_name, xml_names in anafora.walk(args[0], "ADE_entity.dave.completed.xml"):
        textfile_path = join( join(args[0],text_name), text_name)
        with open(textfile_path, 'r') as tf:
            text = tf.read()

        sent_spans = list(sent_tokenizer.span_tokenize(text))
        shutil.copyfile(textfile_path, join(args[1], '%s.txt' % (text_name)))
        brat_out = open( join(args[1], '%s.ann' % (text_name)), 'wt')

        for xml_name in xml_names:
            xml_path = os.path.join(args[0], sub_dir, xml_name)
            xml_parts = xml_name.split('.')
            annotator = xml_parts[2]
            status = xml_parts[3]
            data = anafora.AnaforaData.from_file(xml_path)

            alink_map = {}
            for rel in data.annotations.select_type('ALINK'):
                cat = rel.properties['Type']
                tgt = rel.properties['Target']
                alink_map[tgt.id] = cat

            for annot_ind, annot in enumerate(data.annotations.select_type('Medications/Drugs')):
                id = annot.id
                span = annot.spans[0]
                span_text = text[span[0]:span[1]]
                neg = annot.properties['negation_indicator']
                neg_status = "-1" if neg is None else "1"
                dtr = annot.properties['DocTimeRel']
                if annot.id in alink_map:
                    alink = alink_map[annot.id]
                else:
                    alink = 'None'

                # Write Brat format:
                brat_out.write('T%d\tDrug %d %d\t%s\n' % 
                    (annot_ind, span[0], span[1], span_text))
                #print("File:%s\tID:%s\tSpan:(%d,%d)\tAnnotatedText:%s\tNegated:%s\tDTR:%s\tAlink:%s" %
                #        (text_name, annot.id, span[0], span[1], span_text, neg_status, dtr, alink))
                # Write some ad-hoc format:
                print("File:%s\tSpan:(%d,%d)\tAnnotatedText:%s\tNegated:%s\tDTR:%s\tAlink:%s" %
                        (text_name, span[0], span[1], span_text, neg_status, dtr, alink))

                # Write bert-style tsv for Neg, DTR, ALink:
                covering_sent_span = find_sentence_for_drug(sent_spans, span)
                inst_text = insert_delimiter_for_entity(text, covering_sent_span, span)
 
                neg_out.write('%s\t%s\n' % (neg_status, inst_text))
                dtr_out.write('%s\t%s\n' % (dtr, inst_text))
                alink_out.write('%s\t%s\n' % (alink, inst_text))

        brat_out.close()
    neg_out.close()
    dtr_out.close()
    alink_out.close()

if __name__ == '__main__':
    main(sys.argv[1:])
