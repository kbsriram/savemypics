base=../../android/res/drawable-hdpi

all: $(base)/ic_launcher_smp.png $(base)/snapfish_icon.png $(base)/flickr_icon.png


$(base)/ic_launcher_smp.png: main-icon.svg
	./export_one.sh main-icon.svg ic_launcher_smp.png

$(base)/flickr_icon.png: flickr-icon.svg
	./export_one.sh flickr-icon.svg flickr_icon.png

$(base)/snapfish_icon.png: snapfish-icon.svg
	./export_one.sh snapfish-icon.svg snapfish_icon.png
