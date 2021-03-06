#!/usr/bin/env python3.6
#
import sys
from os.path import join, basename
import argparse
import glob


from nltk import word_tokenize, sent_tokenize
from nltk.tokenize.treebank import TreebankWordTokenizer
from nltk.tokenize.punkt import PunktSentenceTokenizer

from flair.models import SequenceTagger, TextClassifier
from flair.data_fetcher import NLPTaskDataFetcher, NLPTask
from flair.data import Sentence

from brat import read_brat_file
from brat2relations import get_span_ents

parser = argparse.ArgumentParser(description='Flair trainer for consumer health questions')
parser.add_argument('-m', '--model-dir', required=True, help='Trained model')
parser.add_argument('-d', '--data-dir', required=True, help='Directory with Brat-formatted test files')
parser.add_argument('-o', '--output-dir', required=True, help='Directory to write .ann files')

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

    # Loading classifier model:
    print("Loading classifier model")
    classifier = TextClassifier.load_from_file(join(args.model_dir, 'best-model.pt'))

    txt_files = glob.glob(join(args.data_dir, '*.txt'))
    
    sent_splitter = PunktSentenceTokenizer()
    tokenizer = TreebankWordTokenizer()
    sentence_lookahead = 0

    for txt_fn in txt_files:
        print("Processing %s" % (txt_fn))
        ann_input_fn = join(args.data_dir, basename(txt_fn)[:-3]+'ann')
        ents, _ = read_brat_file(ann_input_fn)

        ann_output_fn = join(args.output_dir, basename(txt_fn)[:-3]+'ann')
        with open(txt_fn, 'r') as myfile:
            text = myfile.read()

        ann_out = open(ann_output_fn, 'w')
        
        # Write entities right away:
        for ent_id in ents.keys():
            ent = ents[ent_id]
            ent_text = text[ent.start:ent.end].replace('\n', ' ')
            ann_out.write('%s\t%s %d %d\t%s\n' % (ent_id, ent.cat, ent.start, ent.end, ent_text))

        sent_spans = list(sent_splitter.span_tokenize(text))

        rel_ind = 0
        rel_attempts = 0
        for sent_ind in range(len(sent_spans)):
            primary_sent_span = sent_spans[sent_ind]
            end_window_ind = min(sent_ind+sentence_lookahead, len(sent_spans)-1)
            end_sent_span = sent_spans[end_window_ind]

            sent = text[primary_sent_span[0]:end_sent_span[1]].replace('\n', ' ')
            drug_ents, att_ents = get_span_ents(primary_sent_span, end_sent_span, ents)

            for att_ent in att_ents:
                for drug_ent in drug_ents:
                    ## Get index of ents into sent:
                    a1_start = att_ent.start - primary_sent_span[0]
                    a1_end = att_ent.end - primary_sent_span[0]
                    a1_text = sent[a1_start:a1_end]

                    a2_start = drug_ent.start - primary_sent_span[0]
                    a2_end = drug_ent.end - primary_sent_span[0]
                    a2_text = sent[a2_start:a2_end]

                    if a1_start < a2_start:
                        # arg1 occurs before arg2
                        rel_text = (sent[:a1_start] + 
                                    " %sStart %s %sEnd " % (att_ent.cat, a1_text, att_ent.cat) +
                                    sent[a1_end:a2_start] +
                                    " DrugStart %s DrugEnd" % (a2_text) +
                                    sent[a2_end:])
                    else:
                        rel_text = (sent[:a2_start] +
                                    " DrugStart %s DrugEnd " % (a2_text) +
                                    sent[a2_end:a1_start] +
                                    " %sStart %s %sEnd " % (att_ent.cat, a1_text, att_ent.cat) +
                                    sent[a1_end:])

                    # if att_ent.cat == 'Dosage':
                        # print("working with Dosage ent")
                    sentence = Sentence(rel_text, use_tokenizer=True)
                    labels = classifier.predict(sentence)[0].labels
                    if len(labels) > 1:
                        print('  This relation has more than one output label')
                    label = labels[0].value
                    # print("Comparing ent %s and ent %s and got %s" % (att_ent.id, drug_ent.id, label))
                    rel_attempts += 1
                    if not label == 'None':
                        # Make sure label corresponds to entity type:
                        if label.find(att_ent.cat) < 0:
                            # print("  Skipping found relation where label %s doesn't match arg type %s" % (label, att_ent.cat))
                            continue
                        ann_out.write('R%d\t%s Arg1:%s Arg2:%s\n' % (rel_ind, label, att_ent.id, drug_ent.id))
                        rel_ind += 1

        # print("Finished: Found %d relations while making %d classification attempts" % (rel_ind, rel_attempts))
        ann_out.close()

if __name__ == '__main__':
    main(sys.argv[1:])
