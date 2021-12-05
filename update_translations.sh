#!/bin/bash

# This script assumes that you have an account which can access Unix Launcher translations on https://translate.pokemmo.eu/api/
# After acquiring said account, do `export TL_APIKEY=YOURAPIKEY` in your environment prior to running this script
if [[ -z $TL_APIKEY ]]; then
	echo "TL_APIKEY env variable is unset. Can not access translate.pokemmo.eu"
	exit 1
fi

cd src/main/resources/

TL_LANGCODE=""
OUTPUT_FILENAME=""

for lang in zh fr de it ja ko ptbr es; do
	case $lang in
		zh)	OUTPUT_FILENAME=MessagesBundle_zh.properties ; TL_LANGCODE=zh_Hans ;;
		fr) OUTPUT_FILENAME=MessagesBundle_fr.properties ; TL_LANGCODE=fr ;;
		de) OUTPUT_FILENAME=MessagesBundle_de.properties ; TL_LANGCODE=de ;;
		it) OUTPUT_FILENAME=MessagesBundle_it.properties ; TL_LANGCODE=it ;;
		ja) OUTPUT_FILENAME=MessagesBundle_ja.properties ; TL_LANGCODE=ja ;;
		ko) OUTPUT_FILENAME=MessagesBundle_ko.properties ; TL_LANGCODE=ko ;;
		ptbr) OUTPUT_FILENAME=MessagesBundle_pt_BR.properties ; TL_LANGCODE=pt_BR ;;
		es) OUTPUT_FILENAME=MessagesBundle_es.properties ; TL_LANGCODE=es ;;
	esac
	
	curl -H "Authorization: Token $TL_APIKEY" https://translate.pokemmo.eu/api/translations/client/unix-installer/$TL_LANGCODE/file/ -o $OUTPUT_FILENAME
done


