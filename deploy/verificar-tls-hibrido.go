// Confere se um servidor negocia a troca de chaves híbrida X25519MLKEM768.
//
// Uso (Go 1.24+):
//   go run deploy/verificar-tls-hibrido.go siab.exemplo.com.br:443
//
// O cliente oferece SOMENTE X25519MLKEM768: se o handshake fecha, o
// servidor aceitou o grupo pós-quântico híbrido.
package main

import (
	"crypto/tls"
	"fmt"
	"os"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Println("uso: go run verificar-tls-hibrido.go host:porta [--inseguro]")
		os.Exit(2)
	}
	cfg := &tls.Config{
		MinVersion:       tls.VersionTLS13,
		CurvePreferences: []tls.CurveID{tls.X25519MLKEM768},
		// --inseguro só para testar contra certificado local (tls internal).
		InsecureSkipVerify: len(os.Args) > 2 && os.Args[2] == "--inseguro",
	}
	conn, err := tls.Dial("tcp", os.Args[1], cfg)
	if err != nil {
		fmt.Println("FALHOU: o servidor não aceitou X25519MLKEM768:", err)
		os.Exit(1)
	}
	defer conn.Close()
	estado := conn.ConnectionState()
	fmt.Printf("OK: TLS 1.3 com troca de chaves híbrida X25519MLKEM768 (cifra %s)\n", tls.CipherSuiteName(estado.CipherSuite))
}
