#!/usr/bin/env python3.6
#
import sys
from os.path import join, basename
import argparse
import glob

from nltk import word_tokenize, sent_tokenize
from nltk.tokenize.treebank import TreebankWordTokenizer
from nltk.tokenize.punkt import PunktSentenceTokenizer

from flair.models import SequenceTagger
from flair.data_fetcher import NLPTaskDataFetcher, NLPTask
from flair.data import Sentence

parser = argparse.ArgumentParser(description='Flair trainer for consumer health questions')
parser.add_argument('-m', '--model-dir', required=True, help='Trained model')
parser.add_argument('-d', '--data-dir', required=True, help='Directory with Brat-formatted test files')
parser.add_argument('-o', '--output-dir', required=True, help='Directory to write .ann files')
parser.add_argument('-c', '--conservative', action='store_true', default=False, help='Conservative mode -- only tag non-drug concepts if they occur in a sentence with a drug')

def raw_flair_charmap(raw_sent, flair_sent):
    map = {}
    flair_ind = 0
    raw_ind = 0
    for flair_tok in flair_sent.split(' '):
        raw_ind = raw_sent.find(flair_tok, raw_ind)
        map[flair_ind] = raw_ind
        map[flair_ind + len(flair_tok)] = raw_ind + len(flair_tok)
        flair_ind += len(flair_tok)+1
        raw_ind += len(flair_tok)

    return map


def main(args):
    args = parser.parse_args()

    tagger = SequenceTagger.load_from_file(join(args.model_dir, 'best-model.pt'))

    txt_files = glob.glob(join(args.data_dir, '*.txt'))

    sent_splitter = PunktSentenceTokenizer()
    #tokenizer = TreebankWordTokenizer()

    for txt_fn in txt_files:
        print("Processing %s" % (basename(txt_fn)))
        ann_fn = join(args.output_dir, basename(txt_fn)[:-3]+'ann')
        with open(txt_fn, 'r') as myfile:
            text = myfile.read()

        ann_out = open(ann_fn, 'w')
        ent_id = 0

        sent_spans = sent_splitter.span_tokenize(text)

        raw_offset = 0
        for sent_span in sent_spans:
            raw_offset = sent_span[0]
            sent = text[sent_span[0]:sent_span[1]]
            #tokens = tokenizer.tokenize(sent)
            # tagged = pos_tag(tokens)
            #flair_sent = Sentence(' '.join(tokens))
            flair_sent = Sentence(sent, use_tokenizer=True)
            ade_tagged = tagger.predict(flair_sent)
           
            cmap = raw_flair_charmap(sent, flair_sent.to_tokenized_string()) 
            #print('Sent is %s' % (sent) )
            #print(ade_tagged[0].to_tagged_string())

            # Check for annotated drugs:
            drug_found = False
            for entity in ade_tagged[0].to_dict(tag_type='ner')['entities']:
                if entity['type'] == 'Drug':
                    drug_found = True
                    break
            
            if drug_found or not args.conservative:
                for entity in ade_tagged[0].to_dict(tag_type='ner')['entities']:
                    start = entity['start_pos']
                    end = entity['end_pos']
                    
                    raw_start = start #cmap[start] 
                    raw_end = end #cmap[end]
                    
    #                print('Mapped entity type %s(%s):(%d, %d) => (%d, %d)' % (entity['type'], entity['text'], start, end, raw_offset+raw_start, raw_offset+raw_end) )
                    ann_out.write('T%d\t%s %d %d\t%s\n' % (ent_id, entity['type'], raw_offset+raw_start, raw_offset+raw_end, entity['text']))
                    ent_id += 1

            raw_offset += len(sent)+1

if __name__ == '__main__':
    main(sys.argv[1:])
