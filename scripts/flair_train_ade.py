#!/usr/bin/env python3.6
#
#######################
# flair_train_ade.py
# Train and evaluate the flair embeddings on the chqa
# focus task. 
# This code is borrowed from:
# https://github.com/zalandoresearch/flair/blob/master/resources/docs/TUTORIAL_TRAINING_A_MODEL.md
#######################

import sys
from pathlib import Path

from flair.data import TaggedCorpus
from flair.data_fetcher import NLPTaskDataFetcher, NLPTask
from flair.embeddings import TokenEmbeddings, WordEmbeddings, StackedEmbeddings, CharacterEmbeddings, FlairEmbeddings, BertEmbeddings, ELMoEmbeddings
from typing import List
import argparse

parser = argparse.ArgumentParser(description='Flair trainer for consumer health questions')
parser.add_argument('data_file', nargs=1, help='Conll formatted file with gold standard data')


def main(args):
    args = parser.parse_args()

    # 1. get the corpus
    column_format = {0:'word', 1:'pos', 2:'ner'}

    corpus: TaggedCorpus = NLPTaskDataFetcher.load_column_corpus(Path(args.data_file[0]),
        column_format, 
        tag_to_biloes='ner')
    print(corpus)

    # 2. what tag do we want to predict?
    tag_type = 'ner'

    # 3. make the tag dictionary from the corpus
    tag_dictionary = corpus.make_tag_dictionary(tag_type=tag_type)
    print(tag_dictionary.idx2item)

    # 4. initialize embeddings
    embedding_types: List[TokenEmbeddings] = [

        WordEmbeddings('glove'),

        # comment in this line to use character embeddings
        # CharacterEmbeddings(),

        # comment in these lines to use contextual string embeddings
        FlairEmbeddings('news-forward'),
        FlairEmbeddings('news-backward'),

        # comment in these lines to use Bert embeddings
        # BertEmbeddings(),

        # comment in these lines to use Elmo embeddings
        # ELMoEmbeddings(),
    ]

    embeddings: StackedEmbeddings = StackedEmbeddings(embeddings=embedding_types)

    # 5. initialize sequence tagger
    from flair.models import SequenceTagger

    tagger: SequenceTagger = SequenceTagger(hidden_size=256,
                                            embeddings=embeddings,
                                            tag_dictionary=tag_dictionary,
                                            tag_type=tag_type,
                                            use_crf=True)

    # 6. initialize trainer
    from flair.trainers import ModelTrainer

    trainer: ModelTrainer = ModelTrainer(tagger, corpus)

    # 7. start training
    trainer.train('resources/taggers/glove+flair',
                learning_rate=0.1,
                mini_batch_size=32,
                max_epochs=150)

if __name__ == '__main__':
    main(sys.argv[1:])
