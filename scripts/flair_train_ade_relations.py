import sys
from os.path import join

from flair.data import TaggedCorpus
from flair.data_fetcher import NLPTaskDataFetcher, NLPTask
from flair.embeddings import WordEmbeddings, FlairEmbeddings, DocumentLSTMEmbeddings
from flair.models import TextClassifier
from flair.trainers import ModelTrainer
import argparse


parser = argparse.ArgumentParser(description='Flair trainer for classifying ADE relations')
parser.add_argument('data_file', nargs=1, help='Flair-formatted file with gold standard relation data')

def main(args):
    args = parser.parse_args()

    corpus: TaggedCorpus = NLPTaskDataFetcher.load_classification_corpus(args.data_file[0],
                                                                        train_file='train.txt',
                                                                        dev_file='dev.txt',
                                                                        test_file='test.txt')
    # 2. create the label dictionary
    label_dict = corpus.make_label_dictionary()

    # 3. make a list of word embeddings
    word_embeddings = [WordEmbeddings('glove'),

                    # comment in flair embeddings for state-of-the-art results 
                    FlairEmbeddings('news-forward'),
                    FlairEmbeddings('news-backward'),
                    ]

    # 4. init document embedding by passing list of word embeddings
    document_embeddings: DocumentLSTMEmbeddings = DocumentLSTMEmbeddings(word_embeddings,
                                                                        hidden_size=512,
                                                                        reproject_words=True,
                                                                        reproject_words_dimension=256,
                                                                        )

    # 5. create the text classifier
    classifier = TextClassifier(document_embeddings, label_dictionary=label_dict, multi_label=False)

    # 6. initialize the text classifier trainer
    trainer = ModelTrainer(classifier, corpus)

    # 7. start the training
    model_out = 'resources/classifiers/sentence-classification/glove+flair'
    trainer.train(model_out,
                learning_rate=0.1,
                mini_batch_size=32,
                anneal_factor=0.5,
                patience=5,
                max_epochs=100)

if __name__ == '__main__':
    main(sys.argv[1:])
