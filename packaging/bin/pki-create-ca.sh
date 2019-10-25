#!/bin/sh

CA_DAYS="3650"
KEYTOOL="${JAVA_HOME:-/usr}/bin/keytool"

clean_pki_dir() {
	for f in \
		"${PKIDIR}/cacert.conf" \
		"${PKIDIR}/cert.conf" \
		"${PKIDIR}/serial.txt" \
		"${PKIDIR}/serial.txt.old" \
		"${PKIDIR}/database.txt" \
		"${PKIDIR}/database.txt.old" \
		"${PKIDIR}/database.txt.attr" \
		"${PKIDIR}/database.txt.attr.old" \
		"${PKIDIR}/private/${CA_FILE}.pem" \
		"${PKIDIR}/${CA_FILE}.pem" \
		"${PKIDIR}/certs"/*.cer \
		"${PKIDIR}/certs"/*.pem \
		"${PKIDIR}/keys"/*.p12 \
		"${PKIDIR}/keys"/*.nopass \
		"${PKIDIR}/keys/engine_id_rsa" \
		"${PKIDIR}/requests"/*.req \
		"${PKIDIR}/requests"/*.csr \
	; do
		if [ -e "${f}" ]; then
			common_backup "${f}"
			rm "${f}" || \
				die "Failed to remove ${f}"
		fi
	done
}

config() {
	cp "${PKIDIR}/cacert.template" "${PKIDIR}/cacert.conf" || die "Cannot create cacert.conf"
	cp "${PKIDIR}/cert.template" "${PKIDIR}/cert.conf" || die "Cannot create cert.conf"
	chmod a+r "${PKIDIR}/cacert.conf" "${PKIDIR}/cert.conf" || die "Cannot set config files permissions"
}

enroll() {
	local subject="$1"

	#
	# openssl ca directory must
	# be writable for the user
	# as backup files are produced
	# so let's assume directory
	# is in correct permissions
	#

	echo 1000 > "${PKIDIR}/serial.txt" || die "Cannot write to serial.txt"

	touch "${PKIDIR}/database.txt" "${PKIDIR}/.rnd" || die "Cannot write to database.txt"

	touch "${PKIDIR}/private/${CA_FILE}.pem"
	chmod o-rwx "${PKIDIR}/private/${CA_FILE}.pem" || die "Cannot set CA permissions"
	openssl genrsa \
		-out "${PKIDIR}/private/${CA_FILE}.pem" \
		2048 \
		|| die "Cannot generate CA key"
	openssl req \
		-batch \
		-config "${PKIDIR}/cacert.conf" \
		-new \
		-key "${PKIDIR}/private/${CA_FILE}.pem" \
		-out "${PKIDIR}/requests/${CA_FILE}.csr" \
		-subj "/" \
		|| die "Cannot generate CA request"

	(
		cd "${PKIDIR}"
		openssl ca \
			-batch \
			-config openssl.conf \
			-extfile cacert.conf \
			-extensions v3_ca \
			-in requests/${CA_FILE}.csr \
			-notext \
			-out ${CA_FILE}.pem \
			-keyfile private/${CA_FILE}.pem \
			-selfsign \
			-subj "${subject}" \
			-utf8 \
			-days "${CA_DAYS}" \
			-startdate "$(date --utc --date "now -1 days" +"%y%m%d%H%M%SZ")"
	) || die "Cannot enroll CA certificate"

	return 0
}

renew() {
	openssl x509 \
		-signkey "${PKIDIR}/private/${CA_FILE}.pem" \
		-in "${PKIDIR}/${CA_FILE}.pem" \
		-out "${PKIDIR}/${CA_FILE}.pem.new" \
		-days "${CA_DAYS}" \
		|| die "Cannot renew CA certificate"

	common_backup "${PKIDIR}/${CA_FILE}.pem" || die "Cannot backup CA certificate"
	mv "${PKIDIR}/${CA_FILE}.pem.new" "${PKIDIR}/${CA_FILE}.pem" || die "Cannot install renewed CA certificate"

	return 0
}

keystore() {
	local password="$1"

	"${KEYTOOL}" \
		-delete \
		-noprompt \
		-alias ${CA_FILE}cert \
		-keystore "${PKIDIR}/.truststore" \
		-storepass "${password}" \
		> /dev/null 2>&1
	"${KEYTOOL}" \
		-import \
		-noprompt \
		-trustcacerts \
		-alias ${CA_FILE}cert \
		-keypass "${password}" \
		-file "${PKIDIR}/${CA_FILE}.pem" \
		-keystore "${PKIDIR}/.truststore" \
		-storepass "${password}" \
		|| die "Keystore import failed"
	chmod a+r "${PKIDIR}/.truststore"

	return 0
}

cleanups() {
	openssl x509 -in "${PKIDIR}/${CA_FILE}.pem" -out "${PKIDIR}/certs/${CA_FILE}.der" || die "Cannot read CA certificate"
	chown --reference="${PKIDIR}/private" "${PKIDIR}/private/${CA_FILE}.pem" || die "Cannot set CA private key permissions"
	chmod a+r "${PKIDIR}/${CA_FILE}.pem" "${PKIDIR}/certs/${CA_FILE}.der" || die "Cannot set CA certificate permissions"
	chmod a+r "${PKIDIR}/.truststore"
}

usage() {
	cat << __EOF__
Usage: $0 [OPTIONS]
Create certificate authority.

    --subject=subject              X.500 subject name.
    --keystore-password=password   Password for keystore.
    --ca-file=base-file-name       CA base file name without extension.
    --renew                        Renew CA certificate.
__EOF__
}

. "$(dirname "$(readlink -f "$0")")"/pki-common.sh

cleanup() {
	common_restore_perms "${PKIDIR}"
}

trap cleanup 0
RENEW=
CA_FILE=
while [ -n "$1" ]; do
	x="$1"
	v="${x#*=}"
	shift
	case "${x}" in
		--subject=*)
			SUBJECT="${v}"
		;;
		--keystore-password=*)
			KEYSTORE_PASSWORD="${v}"
		;;
		--ca-file=*)
			CA_FILE="${v}"
		;;
		--renew)
			RENEW=1
		;;
		--help)
			usage
			exit 0
		;;
		--*)
			usage
			exit 1
		;;
	esac
done

[ -z "${RENEW}" -a -z "${SUBJECT}" ] && die "Please specify subject"
[ -n "${KEYSTORE_PASSWORD}" ] || die "Please specify keystore password"
[ -n "${CA_FILE}" ] || die "Please specify CA file"

if [ -z "${RENEW}" ]; then
	clean_pki_dir
	config
	enroll "${SUBJECT}"
else
	renew
fi
keystore "${KEYSTORE_PASSWORD}"
cleanups
