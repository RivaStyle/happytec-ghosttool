GPGKEY      ="8038DEBE14AD09A4"
MFFILE      = build/manifest.mf
LICENCEFILE = build/LICENCE.txt
SHFILE      = build/HTGT_$(version).sh
BATFILE     = build/HTGT_$(version).bat
SIGFILE     = build/HTGT_$(version).sha512.sig
CSUMFILE    = build/HTGT_$(version).sha512
JARFILE     = build/HTGT_$(version).jar
ZIPFILE     = build/HTGT_$(version).zip

JFLAGS  = -g -sourcepath ./classes -classpath ./classes -d ./classes
VMFLAGS = -classpath ./classes
JC      = javac
JAVA    = java
JAR     = jar

sources = $(wildcard classes/*.java)
classes = $(sources:.java=.class)
version = $(strip $(shell $(JAVA) $(VMFLAGS) HTGT -v))
commit  = $(shell git rev-parse --short HEAD)

all: clean patch compile jar zip

patch: clean
	cp -fav src/*.java classes/
	sed -i -r "s/(APPLICATION_VERSION) = \"git-master\";\$$/\1 = \"git-$(commit)\";/" classes/HTGT.java

compile: patch $(classes)

%.class: %.java
	$(JC) $(JFLAGS) $<

jar: compile
	@echo Packaging version $(version)

	@echo "Manifest-Version: 1.0" > $(MFFILE)
	@echo "Class-Path: ." >> $(MFFILE)
	@echo "Main-Class: HTGT" >> $(MFFILE)
	@echo "Permissions: all-permissions" >> $(MFFILE)

	cd ./classes && \
	$(JAR) -cmf ../$(MFFILE) ../$(JARFILE) ./*.class && \
	chmod +x ../$(JARFILE) && $(RM) ../$(MFFILE)

zip: jar
	@echo Zipping version $(version)

	cp -af HTGT.sh $(SHFILE)
	sed -i "s/HTGT.jar/HTGT_$(version).jar/" $(SHFILE)

	cp -af HTGT.bat $(BATFILE)
	sed -i "s/HTGT.jar/HTGT_$(version).jar/" $(BATFILE)

	cp -af LICENCE $(LICENCEFILE)
	unix2dos $(LICENCEFILE)

	zip -j $(ZIPFILE) $(LICENCEFILE) $(JARFILE) $(SHFILE) $(BATFILE)
	$(RM) $(LICENCEFILE) $(SHFILE) $(BATFILE)

sig: zip
	@echo Signing version $(version)

	sha512sum $(JARFILE) $(ZIPFILE) > $(CSUMFILE)
	sed -i 's#build/##' $(CSUMFILE) && $(RM) $(SIGFILE)
	gpg -u $(GPGKEY) --armor --output $(SIGFILE) --detach-sig $(CSUMFILE)

clean:
	$(RM) build/HTGT_*.*
	$(RM) $(MFFILE) $(LICENCEFILE)
	$(RM) classes/*.java classes/*.class
